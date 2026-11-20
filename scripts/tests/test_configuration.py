import gzip
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from configuration import database, deployment
from configuration.database_client import command as database_command
from configuration.dotenv import parse, read
from configuration.environment import defaults, load
from configuration.processes import build_environment, java_command
from configuration.validation import ConfigurationError, validate


class ConfigurationTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.base = Path(self.temporary.name)

    def tearDown(self):
        self.temporary.cleanup()

    def file(self, contents, name="app.env"):
        path = self.base / name
        with path.open('w',encoding='utf-8',newline='') as stream:
            stream.write(contents)
        return path

    def test_dotenv_handles_bom_crlf_chinese_json_and_literal_shell(self):
        values = parse('\ufeffexport SMS_SIGN_NAME=素见好物\r\n'
                       'DATABASE_URL=jdbc:mysql://db/shop?a=1&b=2\r\n'
                       'WX_TRANSFER_REPORT_INFOS=\'{"note": "a # b", "label": "中文"}\'\r\n'
                       'VALUE=$(touch forbidden) `echo unchanged`\r\n'
                       'QUOTED="line one\\nline two # still data" # comment\r\n')
        self.assertEqual(values['SMS_SIGN_NAME'], '素见好物')
        self.assertEqual(values['DATABASE_URL'], 'jdbc:mysql://db/shop?a=1&b=2')
        self.assertEqual(json.loads(values['WX_TRANSFER_REPORT_INFOS'])['note'], 'a # b')
        self.assertEqual(values['VALUE'], '$(touch forbidden) `echo unchanged`')
        self.assertEqual(values['QUOTED'], 'line one\nline two # still data')

    def test_multiline_and_internal_json_quotes_preserve_hash(self):
        values = parse('MULTI="first\nsecond"\nJSON={"inside":"a # b"} # comment\nEMPTY=\n')
        self.assertEqual(values['MULTI'], 'first\nsecond')
        self.assertEqual(values['JSON'], '{"inside":"a # b"}')
        self.assertEqual(values['EMPTY'], '')

    def test_bad_file_diagnostics_do_not_contain_secret_values(self):
        secret = 'never-print-private-value'
        with self.assertRaises(ValueError) as result:
            parse('WRONG KEY=' + secret)
        self.assertNotIn(secret, str(result.exception))
        with self.assertRaises(ValueError):
            parse('SECRET="unclosed')
        with self.assertRaises(ValueError):
            parse('SECRET=bad\0value')
        invalid = self.base / 'invalid.env'
        invalid.write_bytes(b'PASSWORD=\xff')
        with self.assertRaisesRegex(ValueError, '配置文件不可读取'):
            read(invalid)

    def test_explicit_empty_config_never_discovers_repository_dotenv(self):
        def metadata_only(path):
            self.assertEqual(path.name, '.env.example', 'must not read private config')
            return read(path)
        with patch('configuration.environment.read', side_effect=metadata_only):
            values, _, selected = load(inherited={'APP_CONFIG_FILE': ''})
        self.assertIsNone(selected)
        self.assertEqual(values['APP_CONFIG_FILE'], '')

    def test_explicit_missing_config_fails_without_exposing_input(self):
        with self.assertRaisesRegex(ValueError, '指定的配置文件不存在'):
            load(str(self.base / 'missing.env'), inherited={})

    def test_file_secret_precedence_and_preserved_whitespace(self):
        self.file(' file value \r\n', 'key.txt')
        config = self.file('SMS_ACCESS_KEY_SECRET=lower-direct\nIDENTITY_ACCESS_KEY_SECRET_FILE=key.txt\n')
        values, _, _ = load(str(config), inherited={'SMS_ACCESS_KEY_SECRET_FILE': 'key.txt'})
        self.assertEqual(values['SMS_ACCESS_KEY_SECRET'], ' file value ')
        self.assertEqual(values['IDENTITY_ACCESS_KEY_SECRET'], ' file value ')
        values, _, _ = load(str(config), inherited={'SMS_ACCESS_KEY_SECRET': 'process-value', 'SMS_ACCESS_KEY_SECRET_FILE': 'absent'})
        self.assertEqual(values['SMS_ACCESS_KEY_SECRET'], 'process-value')

    def test_explicit_empty_value_wins_over_lower_file(self):
        config = self.file('SMS_ACCESS_KEY_SECRET_FILE=does-not-exist\n')
        values, _, _ = load(str(config), inherited={'SMS_ACCESS_KEY_SECRET': ''})
        self.assertEqual(values['SMS_ACCESS_KEY_SECRET'], '')

    def test_relative_paths_and_restore_file_are_data_not_secret_indirection(self):
        config = self.file('WX_PRIVATE_KEY_PATH=keys/merchant.pem\nBACKUP_DIR=backups\nRESTORE_FILE=backups/test.sql.gz\n')
        values, base, selected = load(str(config), inherited={})
        self.assertEqual(base, self.base)
        self.assertEqual(selected, config)
        self.assertEqual(values['WX_PRIVATE_KEY_PATH'], str(self.base / 'keys/merchant.pem'))
        self.assertEqual(values['RESTORE_FILE'], str(self.base / 'backups/test.sql.gz'))
        self.assertNotIn('RESTORE', values)

    def test_mysql_domain_callbacks_and_port_are_derived_from_one_config(self):
        values = {'MYSQL_DATABASE':'shop', 'MYSQL_USER':'store', 'MYSQL_PASSWORD':'fixture-value',
                  'PUBLIC_STORE_URL':'https://store.example.com', 'PORT':'9090'}
        defaults(values)
        self.assertIn('jdbc:mysql://mysql:3306/shop?', values['DATABASE_URL'])
        self.assertEqual(values['DATABASE_PASSWORD'], 'fixture-value')
        self.assertEqual(values['WX_REFUND_NOTIFY_URL'], 'https://store.example.com/api/v1/callbacks/wechat/refund')
        self.assertEqual(values['API_UPSTREAM_URL'], 'http://server:9090')

    def test_external_database_does_not_fall_back_to_embedded_defaults(self):
        values = {'DATABASE_MODE':'external','MYSQL_DATABASE':'local','MYSQL_USER':'local','MYSQL_PASSWORD':'local-value'}
        defaults(values)
        for name in ['DATABASE_URL','DATABASE_USER','DATABASE_PASSWORD']:
            self.assertNotIn(name,values)

    def test_builds_isolate_runtime_credentials_and_database_targets(self):
        values = {'PATH':os.environ.get('PATH',''), 'SMS_ACCESS_KEY_SECRET':'private-value',
                  'ACCOUNT_DATA_KEY':'private-value', 'TEST_DATABASE_URL':'jdbc:mysql://production/live',
                  'ACCOUNT_DATA_KEY_FILE':'/private/account.key', 'SUPPORT_CONTACT_FILE':'/private/contact.txt',
                  'TEST_DATABASE_PASSWORD':'private-database-password',
                  'APP_CONFIG_FILE':'/private/app.env', 'WX_PRIVATE_KEY_PATH':'/private/key',
                  'HTTPS_PROXY':'https://proxy.example:443'}
        safe = build_environment(values)
        for name in ['SMS_ACCESS_KEY_SECRET','ACCOUNT_DATA_KEY','ACCOUNT_DATA_KEY_FILE','SUPPORT_CONTACT_FILE','TEST_DATABASE_URL','TEST_DATABASE_PASSWORD','WX_PRIVATE_KEY_PATH']:
            self.assertNotIn(name,safe)
        self.assertEqual(safe['APP_CONFIG_FILE'],'')
        self.assertIn('HTTPS_PROXY',safe)

    def test_process_only_known_file_settings_resolve_but_host_tool_files_do_not(self):
        secret = self.file('fixture-account-key\r\n', 'account.key')
        contact = self.file('素见客服\n', 'contact.txt')
        port = self.file('9090\n', 'port.txt')
        values, _, _ = load(inherited={
            'APP_CONFIG_FILE':'', 'ACCOUNT_DATA_KEY_FILE':str(secret),
            'SUPPORT_CONTACT_FILE':str(contact), 'PRIVACY_CONTACT_FILE':str(contact),
            'PORT_FILE':str(port), 'AWS_SHARED_CREDENTIALS_FILE':str(self.base/'must-not-read')})
        self.assertEqual(values['ACCOUNT_DATA_KEY'],'fixture-account-key')
        self.assertEqual(values['SUPPORT_CONTACT'],'素见客服')
        self.assertEqual(values['PRIVACY_CONTACT'],'素见客服')
        self.assertEqual(values['PORT'],'9090')
        self.assertNotIn('AWS_SHARED_CREDENTIALS',values)

    def test_runtime_plan_contains_references_and_readonly_key_mounts_not_credentials(self):
        merchant = self.file('fixture-merchant-content','merchant.pem')
        public = self.file('fixture-public-content','public.pem')
        values = {'WX_PRIVATE_KEY_PATH':str(merchant),'WX_PLATFORM_PUBLIC_KEY_PATH':str(public),
                  'ACCOUNT_DATA_KEY':'private-runtime-value','APP_NETWORK_CIDR':'172.30.10.0/24','PORT':'9090'}
        path = deployment.prepare(values, self.base / 'runtime')
        text = path.read_text()
        self.assertNotIn('private-runtime-value',text)
        self.assertNotIn('fixture-merchant-content',text)
        plan = json.loads(text)
        self.assertEqual(plan['services']['server']['environment']['ACCOUNT_DATA_KEY'],'${ACCOUNT_DATA_KEY}')
        self.assertEqual(plan['services']['server']['environment']['APP_CONFIG_FILE'],'')
        self.assertEqual(plan['services']['server']['environment']['PORT'],'${PORT}')
        self.assertTrue(all(mount['read_only'] for mount in plan['services']['server']['volumes']))
        if os.name!='nt':
            self.assertEqual(path.stat().st_mode & 0o777,0o600)
            self.assertEqual(path.parent.stat().st_mode & 0o777,0o700)

    def test_json_java_arguments_preserve_windows_paths_without_shell(self):
        jar = self.base / 'app.jar'
        with patch('configuration.processes.java_executable', return_value='java'):
            command = java_command({'JAVA_OPTS_JSON':json.dumps(['-Dfile=C:\\store files\\trust.p12'])}, jar)
        self.assertEqual(command[1],'-Dfile=C:\\store files\\trust.p12')
        self.assertEqual(command[-2:],['-jar',str(jar)])

    def test_validation_aggregates_names_without_credential_values(self):
        with patch('configuration.validation.validate_key'), self.assertRaises(ConfigurationError) as result:
            validate({'APP_DEV_MODE':'true','DATABASE_PASSWORD':'never-log-this-value','PUBLIC_STORE_URL':'http://invalid'})
        message = str(result.exception)
        self.assertIn('APP_DEV_MODE',message)
        self.assertIn('SMS_ACCESS_KEY_SECRET',message)
        self.assertNotIn('never-log-this-value',message)

    def test_external_backup_preserves_tls_and_keeps_password_out_of_arguments(self):
        values = {'DATABASE_MODE':'external','DATABASE_URL':'jdbc:mysql://db.example.com:3307/shop?sslMode=VERIFY_IDENTITY',
                  'DATABASE_USER':'store','DATABASE_PASSWORD':'secret-with-$()-and-spaces'}
        with patch('configuration.database_client.executable', return_value='docker'):
            command, environment = database_command(values,dump=True)
        self.assertIn('--ssl-mode=VERIFY_IDENTITY',command)
        self.assertIn('--host=db.example.com',command)
        self.assertIn('--port=3307',command)
        self.assertNotIn(values['DATABASE_PASSWORD'],' '.join(command))
        self.assertEqual(environment['MYSQL_PWD'],values['DATABASE_PASSWORD'])

    def test_external_backup_rejects_nonstandard_or_embedded_credentials(self):
        values = {'DATABASE_MODE':'external','DATABASE_URL':'jdbc:mysql://user:password@db.example.com/shop',
                  'DATABASE_USER':'store','DATABASE_PASSWORD':'fixture-value'}
        with self.assertRaises(ValueError):
            database_command(values,dump=False)

    def test_external_restore_keeps_container_stdin_open_without_exposing_password(self):
        values = {'DATABASE_MODE':'external','DATABASE_URL':'jdbc:mysql://db.example.com/store_test?sslMode=REQUIRED',
                  'DATABASE_USER':'store','DATABASE_PASSWORD':'test-only-file-secret'}
        with patch('configuration.database_client.executable', return_value='docker'):
            command, environment = database_command(values,dump=False)
            dump, _ = database_command(values,dump=True)
        self.assertIn('-i',command)
        self.assertNotIn('-i',dump)
        self.assertEqual(command[command.index('--entrypoint')+1],'mysql')
        self.assertNotIn(values['DATABASE_PASSWORD'],' '.join(command))
        self.assertEqual(environment['MYSQL_PWD'],values['DATABASE_PASSWORD'])

    def test_backup_produces_distinct_verified_archives(self):
        command = [sys.executable, '-c', 'import sys;sys.stdout.write("CREATE TABLE audit(id INT);\\n")']
        with patch('configuration.database.client_command', return_value=(command, dict(os.environ))):
            first = database.backup({}, str(self.base))
            second = database.backup({}, str(self.base))
        self.assertNotEqual(first, second)
        self.assertEqual(gzip.decompress(first.read_bytes()), b'CREATE TABLE audit(id INT);\n')
        database._verify(first)
        database._verify(second)
        if os.name != 'nt':
            self.assertEqual(first.stat().st_mode & 0o777, 0o600)

    def test_backup_failure_removes_output_and_redacts_database_errors(self):
        command = [sys.executable, '-c', 'import sys;sys.stderr.write("never-print-private-password");sys.exit(2)']
        with patch('configuration.database.client_command', return_value=(command, dict(os.environ))):
            with self.assertRaises(RuntimeError) as result:
                database.backup({}, str(self.base))
        self.assertNotIn('never-print-private-password', str(result.exception))
        self.assertFalse(list(self.base.glob('*.sql.gz*')))

    def test_restore_rejects_missing_confirmation_bad_checksum_and_corrupt_archive(self):
        source = self.base / 'snapshot.sql.gz'
        source.write_bytes(b'not a gzip archive')
        checksum = source.with_name(source.name + '.sha256')
        with self.assertRaisesRegex(ValueError, '--confirm'):
            database.restore({}, str(source), False)
        checksum.write_text('')
        with self.assertRaisesRegex(ValueError, '格式不正确'):
            database._verify(source)
        checksum.write_text('0' * 64)
        with self.assertRaisesRegex(ValueError, '校验失败'):
            database._verify(source)
        checksum.write_text(hashlib.sha256(source.read_bytes()).hexdigest())
        with self.assertRaisesRegex(ValueError, '已损坏'):
            database._verify(source)


if __name__=='__main__':
    unittest.main()

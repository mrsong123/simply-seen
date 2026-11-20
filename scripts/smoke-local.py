#!/usr/bin/env python3
"""Verify a clean local install through real HTTP, with no merchant credentials."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]


def free_port() -> int:
    with socket.socket() as connection:
        connection.bind(("127.0.0.1", 0))
        return connection.getsockname()[1]


def call(base, path, method="GET", body=None, token=None, request_key=None, status=200):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if request_key:
        headers["Idempotency-Key"] = request_key
    request = urllib.request.Request(
        base + "/api/v1" + path,
        data=None if body is None else json.dumps(body).encode(),
        method=method,
        headers=headers,
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            actual, payload = response.status, json.load(response)
    except urllib.error.HTTPError as error:
        actual, payload = error.code, json.loads(error.read())
    assert actual == status, f"{method} {path}: expected HTTP {status}, got {actual}; code={payload.get('code')}"
    if status == 200:
        assert payload.get("code") == "OK", f"{path}: invalid API envelope"
        return payload.get("data")
    return payload


def start(java, jar, workdir, environment, port, name):
    log_dir = ROOT / "server/target/local-smoke-logs"
    log_dir.mkdir(parents=True, exist_ok=True)
    log = log_dir / (name + "-" + uuid.uuid4().hex[:8] + ".log")
    stream = log.open("x")
    child_env = {**environment, "PORT": str(port)}
    process = subprocess.Popen([java, "-jar", str(jar)], cwd=workdir, env=child_env,
                               stdout=stream, stderr=subprocess.STDOUT)
    return process, stream, log


def stop(process, stream):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=25)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)
    stream.close()


def ready(process, port, log):
    base = f"http://127.0.0.1:{port}"
    for _ in range(90):
        if process.poll() is not None:
            raise AssertionError(f"Local startup exited with {process.returncode}; log: {log}")
        try:
            with urllib.request.urlopen(base + "/actuator/health", timeout=1) as response:
                if json.load(response).get("status") == "UP":
                    # Readiness includes ApplicationRunner/bootstrap, not only the listening port.
                    config = call(base, "/config")
                    if config.get("devMode") and call(base, "/products"):
                        return base
        except (OSError, AssertionError, ValueError):
            pass
        time.sleep(1)
    raise AssertionError(f"Local readiness timed out; log: {log}")


def smoke(java, jar, result_file):
    # Preserve proxy/CA/auth guard variables; remove only application configuration.
    environment = os.environ.copy()
    names = [line.split("=", 1)[0] for line in (ROOT / ".env.example").read_text().splitlines()
             if "=" in line and not line.startswith("#")]
    for name in names + ["SPRING_PROFILES_ACTIVE", "SPRING_PROFILES_DEFAULT",
                         "APP_BOOTSTRAP_CREDENTIALS_PATH", "SPRING_CONFIG_LOCATION",
                         "SPRING_CONFIG_ADDITIONAL_LOCATION"]:
        environment.pop(name, None)
        environment.pop(name + "_FILE", None)
    environment["APP_CONFIG_FILE"] = ""
    environment["JOB_INITIAL_DELAY_MS"] = "3600000"
    with tempfile.TemporaryDirectory(prefix="sujian-clean-local-") as temporary:
        workdir = Path(temporary)
        port = free_port()
        process, stream, log = start(java, jar, workdir, environment, port, "first-start")
        try:
            base = ready(process, port, log)
            credentials_path = workdir / "data/bootstrap-admin.txt"
            assert credentials_path.is_file(), "Clean local startup did not create private credentials"
            original_credentials = credentials_path.read_bytes()
            credentials = dict(line.split("=", 1) for line in original_credentials.decode().splitlines() if "=" in line)
            assert len(credentials["password"]) >= 24
            assert credentials["password"] not in log.read_text(), "Bootstrap password leaked to logs"
            admin = call(base, "/auth/admin", "POST", credentials)["token"]
            legacy = call(base, "/auth/dev", "POST", {"name": "LocalSmoke" + uuid.uuid4().hex[:8]})
            binding = call(base, "/me/phone/code", "POST", {"phone": "13700000001"}, legacy["token"])
            bound = call(base, "/me/phone", "POST", {
                "phone": "13700000001", "code": binding["devCode"],
                "challengeId": binding["challengeId"],
            }, legacy["token"])
            assert bound["id"] == legacy["profile"]["id"] and bound["phoneBound"]
            challenge = call(base, "/auth/phone/code", "POST", {"phone": "13800000001"})
            assert challenge["development"] and len(challenge["devCode"]) == 6
            login_body = {"phone": "13800000001", "code": challenge["devCode"],
                          "challengeId": challenge["challengeId"], "accepted": True}
            session = call(base, "/auth/phone", "POST", login_body)
            member = session["token"]
            assert session["profile"]["phoneBound"] and not session["profile"]["wechatBound"]
            replay_code = call(base, "/auth/phone", "POST", login_body, status=400)
            assert replay_code["code"] == "SMS_CODE_USED", f"Unexpected code rejection: {replay_code.get('code')}"
            identity_gate = call(base, "/me/withdrawals", "POST", {"amountFen": 1000},
                                 member, uuid.uuid4().hex, status=409)
            assert identity_gate["code"] == "IDENTITY_REQUIRED"
            identity = call(base, "/me/identity", "POST", {
                "name": "开发验收", "idNumber": "11010519491231002X", "accepted": True,
            }, member)
            assert identity["status"] == "VERIFIED" and identity["development"]
            assert identity["idNumberMasked"] != "11010519491231002X"
            original_data_key = (workdir / "data/account-data.key").read_bytes()
            call(base, "/admin/orders", token=member, status=403)
            product = call(base, "/products")[0]
            catalogue = call(base, "/products/page?size=2&page=1&sort=priceUp")
            assert catalogue["total"] >= 3 and len(catalogue["items"]) == 2
            call(base, "/admin/records/products", token=member, status=403)
            assert call(base, "/admin/records/products", token=admin)["total"] >= 3
            sku = call(base, "/products/" + str(product["id"]))["skus"][0]
            address = call(base, "/addresses", "POST", {
                "name": "本地启动验收", "phone": "13800138000", "region": "浙江省 杭州市 西湖区",
                "detail": "启动验证路1号", "is_default": True,
            }, member)
            quote = call(base, "/checkout/quote", "POST", {
                "items": [{"skuId": sku["id"], "quantity": 1}], "addressId": address["id"],
                "pointsRequested": 0,
            }, member)
            assert all("cost" not in item and "commissionPercent" not in item for item in quote["items"])
            assert "beneficiaries" not in quote["rule"] and "commissionBudgetFen" not in quote["rule"]
            body = {"quoteId": quote["quoteId"], "addressId": address["id"], "buyerRemark": "本地启动验证"}
            request_key = uuid.uuid4().hex
            order = call(base, "/orders", "POST", body, member, request_key)
            replay = call(base, "/orders", "POST", body, member, request_key)
            assert order["id"] == replay["id"], "Order creation was not idempotent"
            assert "rule_snapshot" not in order and "payment_payer_openid" not in order
            assert all("cost" not in item for item in order["items"])
            owned_page = call(base, "/me/records/orders", token=member)
            assert owned_page["items"][0]["id"] == order["id"]
            order_path = "/orders/" + order["id"]
            call(base, "/dev" + order_path + "/pay", "POST", {}, member)
            call(base, "/admin" + order_path + "/ship", "POST", {
                "carrier": "顺丰速运", "trackingNo": "SF12345678901",
            }, admin)
            call(base, order_path + "/receive", "POST", {}, member)
            detail = call(base, order_path, token=member)
            assert detail["state"] == "COMPLETED"
            refund = call(base, "/refunds", "POST", {
                "orderNo": order["id"], "reason": "本地完整链路验收退款",
                "lines": [{"orderItemId": item["id"], "quantity": item["quantity"]} for item in detail["items"]],
            }, member, uuid.uuid4().hex)
            call(base, "/admin/refunds/" + refund["id"] + "/review", "POST", {
                "approve": True, "reason": "核对订单与退款依据通过",
            }, admin)
            for _ in range(10):
                if call(base, order_path, token=member)["state"] == "REFUNDED":
                    break
                time.sleep(1)
            else:
                raise AssertionError("Approved development refund did not complete")
        finally:
            stop(process, stream)
        process, stream, log = start(java, jar, workdir, environment, port, "second-start")
        try:
            base = ready(process, port, log)
            assert credentials_path.read_bytes() == original_credentials, "Restart reset local credentials"
            call(base, "/auth/admin", "POST", credentials)
            assert (workdir / "data/account-data.key").read_bytes() == original_data_key
            profile = call(base, "/me", token=member)
            assert profile["id"] == session["profile"]["id"] and profile["phoneBound"]
            assert call(base, "/me/identity", token=member)["status"] == "VERIFIED"
        finally:
            stop(process, stream)
        production_env = {**environment, "SPRING_PROFILES_ACTIVE": "prod", "APP_DEV_MODE": "false"}
        process, stream, log = start(java, jar, workdir, production_env, free_port(), "production-guard")
        try:
            result = process.wait(timeout=45)
            assert result != 0, "Production startup accepted missing merchant/database configuration"
        finally:
            stop(process, stream)
    summary = (
        "Clean local profile: ready without .env or merchant credentials\n"
        "Private random bootstrap credentials: created, not logged, stable after restart\n"
        "Phone authentication: auto-register, single-use code, original-account phone binding: passed\n"
        "Identity: explicit development verification, masked fields, withdrawal gate: passed\n"
        "Private account encryption key and identity/profile: stable after restart\n"
        "Server-side catalogue/admin/member pagination and member financial projection: passed\n"
        "Real HTTP: login, permission rejection, address, quote, idempotent order, development pay, "
        "shipment, receipt, refund: passed\n"
        "Production without required configuration: startup rejected\n"
        "No real merchant transaction was performed.\n"
    )
    if result_file:
        result_file.parent.mkdir(parents=True, exist_ok=True)
        result_file.write_text(summary)
    print(summary, end="")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, default=ROOT / "server/target/mall-1.0.0.jar")
    parser.add_argument("--java", default=shutil.which("java"))
    parser.add_argument("--result", type=Path)
    arguments = parser.parse_args()
    if not arguments.java:
        parser.error("Java 21 runtime was not found")
    if not arguments.jar.is_file():
        parser.error("Build the backend JAR first")
    smoke(arguments.java, arguments.jar.resolve(), arguments.result)

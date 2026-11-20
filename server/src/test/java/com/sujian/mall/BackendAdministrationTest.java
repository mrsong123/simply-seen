package com.sujian.mall;

import static com.sujian.mall.common.BusinessSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sujian.mall.common.Problem;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class BackendAdministrationTest extends BackendOperationsFixture {
  @Test void missingTargetsNeverPretendToBeChanged() {
    assertEquals(404,assertThrows(Problem.class,()->admin.block(0,-987654321,Map.of("blocked",true))).status);
    assertEquals(404,assertThrows(Problem.class,()->admin.resolve(0,-987654321,Map.of("reason","已核对处理依据"))).status);
  }

  @Test void resolvedRecurringProblemReopensTheSameIssueWithItsNewEvidence() {
    String key="ops-issue-"+unique(); support.issue("OPS_TEST",key,"首次问题");
    long id=jdbc.queryForObject("SELECT id FROM issue WHERE kind='OPS_TEST' AND resource=?",Long.class,key);
    admin.resolve(0,id,Map.of("reason","已核对原始处理记录"));
    assertEquals("RESOLVED",jdbc.queryForObject("SELECT state FROM issue WHERE id=?",String.class,id));
    assertThrows(Problem.class,()->admin.resolve(0,id,Map.of("reason","重复关闭不能伪装成功")));
    support.issue("OPS_TEST",key,"同一问题再次发生的新证据");
    assertEquals("OPEN",jdbc.queryForObject("SELECT state FROM issue WHERE id=?",String.class,id));
    assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM issue WHERE kind='OPS_TEST' AND resource=?",Integer.class,key));
    assertEquals("同一问题再次发生的新证据",jdbc.queryForObject("SELECT detail FROM issue WHERE id=?",String.class,id));
  }

  @Test void blockingMemberRevokesSessionsAndUnblockingDoesNotResurrectAnOldToken() {
    var login=auth.devLogin(Map.of("name","Gate"+unique()));
    long id=auth.member(request(s(login,"token"))).id();
    admin.block(0,id,Map.of("blocked",true));
    assertEquals(401,assertThrows(Problem.class,()->auth.member(request(s(login,"token")))).status);
    admin.block(0,id,Map.of("blocked",false));
    assertEquals(401,assertThrows(Problem.class,()->auth.member(request(s(login,"token")))).status);
  }

  @Test void simultaneousAdministratorDisablesCannotRemoveEveryAdministrator() throws Exception {
    var before=jdbc.queryForList("SELECT id,active FROM admin_account WHERE role='ADMIN'");
    String a="ops_a_"+unique(),b="ops_b_"+unique();
    auth.createAdmin(0,Map.of("username",a,"password","ops-fixture-password-2026","role","ADMIN"));
    auth.createAdmin(0,Map.of("username",b,"password","ops-fixture-password-2026","role","ADMIN"));
    long aid=jdbc.queryForObject("SELECT id FROM admin_account WHERE username=?",Long.class,a);
    long bid=jdbc.queryForObject("SELECT id FROM admin_account WHERE username=?",Long.class,b);
    jdbc.update("UPDATE admin_account SET active=FALSE WHERE role='ADMIN' AND id NOT IN (?,?)",aid,bid);
    var workers=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
    try {
      var first=workers.submit(()->disable(start,aid,bid)); var second=workers.submit(()->disable(start,bid,aid));
      start.countDown();
      int successes=(first.get(15,TimeUnit.SECONDS)?1:0)+(second.get(15,TimeUnit.SECONDS)?1:0);
      assertEquals(1,successes);
      assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM admin_account WHERE role='ADMIN' AND active=TRUE",Integer.class));
    } finally {
      workers.shutdownNow(); jdbc.update("UPDATE admin_account SET active=FALSE WHERE id IN (?,?)",aid,bid);
      for(var row:before)jdbc.update("UPDATE admin_account SET active=? WHERE id=?",row.get("active"),row.get("id"));
    }
  }
  private boolean disable(CountDownLatch start,long actor,long target) {
    try { start.await(); admin.accountActive(actor,target,Map.of("active",false)); return true; }
    catch(Problem expected){return false;}
    catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new RuntimeException(interrupted);}
  }

  @Test void roleAndMemberGatesAreEnforcedByActualHttpControllers() throws Exception {
    for(String role:List.of("OPERATOR","FINANCE")){
      String username="ops_"+unique(); auth.createAdmin(0,Map.of("username",username,"password","ops-fixture-password-2026","role",role));
      String token=s(auth.adminLogin(Map.of("username",username,"password","ops-fixture-password-2026"),"test-"+username),"token");
      mvc.perform(get("/api/v1/admin/accounts").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
      mvc.perform(get(role.equals("OPERATOR")?"/api/v1/admin/ledger":"/api/v1/admin/products").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
      mvc.perform(get(role.equals("OPERATOR")?"/api/v1/admin/products":"/api/v1/admin/ledger").header("Authorization","Bearer "+token)).andExpect(status().isOk());
    }
    var member=auth.devLogin(Map.of("name","Http"+unique()));
    mvc.perform(get("/api/v1/admin/summary").header("Authorization","Bearer "+s(member,"token"))).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
  }
}

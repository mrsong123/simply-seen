#!/usr/bin/env python3
"""Emit the implemented API contract; fail if an endpoint is undocumented."""
from pathlib import Path
import json,re
root=Path(__file__).resolve().parents[1]
S={}
def obj(name,fields,required=None):
    S[name]={'type':'object','properties':fields,'required':required if required is not None else list(fields)}
def ref(name):return {'$ref':'#/components/schemas/'+name}
def arr(schema):return {'type':'array','items':schema}
def string(description=''):return {'type':'string',**({'description':description} if description else {})}
def num(description=''):return {'type':'integer','format':'int64',**({'description':description} if description else {})}
def boolean():return {'type':'boolean'}
def fields(names,kind=string):return {n:kind() for n in names.split()}
obj('Address',{**fields('id member_id',num),**fields('name phone region detail'),'is_default':boolean()},['name','phone','region','detail','is_default'])
obj('Sku',{**fields('id product_id price cost stock held version',num),**fields('name code')},['name','code','price','cost','stock'])
obj('Product',{**fields('id category_id points_percent commission_percent created_at price stock',num),**fields('name subtitle description image color status'),'featured':boolean(),'skus':arr(ref('Sku'))},['name','category_id','subtitle','description','image','color','status','points_percent','commission_percent','featured','skus'])
obj('Category',{**fields('id sort_no',num),'name':string()},['name','sort_no'])
obj('Member',{**fields('id inviter_id points points_held available reserved debt created_at',num),**fields('name invite_code'),'promoter':boolean(),'blocked':boolean()},['id','name','invite_code','points','points_held','available','reserved','debt','promoter'])
obj('Admin',{**fields('id',num),**fields('username role'),'active':boolean()},['id','username','role'])
obj('Rule',{**fields('level1Bps level2Bps marginSharePercent settlementDays pointsPer100Yuan freightFen freeShippingFen monthlyBudgetFen monthlyCapFen monthlyPoolBps commissionBudgetFen',num),'distributionEnabled':boolean()})
obj('RuleVersion',{**fields('id creator reviewer created_at',num),**fields('state config')})
obj('QuoteItem',{'skuId':num(),'productId':num(),'name':string(),'image':string(),'quantity':num(),'unitPrice':num('分'),'cost':num('分'),'commissionPercent':num(),'amount':num('分'),'maxPoints':num('点'),'points':num('点'),'cash':num('分')})
obj('Quote',{'quoteId':string(),'items':arr(ref('QuoteItem')),**fields('itemAmountFen freightFen pointsUsed pointsAllowed cashDueFen expiresAt',num),'address':ref('Address'),'rule':ref('Rule')})
obj('OrderItem',{**fields('id sku_id product_id quantity unit_price cost cash_amount points_amount commission_percent refunded_quantity refunded_cash refunded_points',num),**fields('order_id name image')})
obj('Refund',{**fields('id order_id state line_snapshot reason channel_id'),**fields('member_id amount points creator reviewer created_at',num)})
obj('Order',{**fields('id state payment_state address_snapshot rule_snapshot remark transaction_id carrier tracking_no'),**fields('member_id item_amount freight points_used cash_due cash_refunded points_refunded expires_at created_at paid_at completed_at',num),'items':arr(ref('OrderItem')),'address':ref('Address'),'refunds':arr(ref('Refund'))},['id','member_id','state','payment_state','cash_due','items'])
obj('CartItem',{**fields('sku_id product_id quantity price stock',num),**fields('sku_name name image color status')})
obj('Ledger',{**fields('id member_id amount balance created_at',num),**fields('event_key asset order_id note')})
obj('Commission',{**fields('id beneficiary_id buyer_id level_no amount original_amount settled_at created_at',num),**fields('order_id state rule_snapshot')})
obj('Withdrawal',{**fields('member_id gross tax net reviewer executor created_at',num),**fields('id state channel_id package_info reason')})
obj('Award',{**fields('id settlement_id member_id sales amount rank_no',num)})
obj('Settlement',{**fields('id budget creator reviewer created_at',num),**fields('period state'),'awards':arr(ref('Award'))})
obj('ReconcileBatch',{**fields('id orders_total refunds_total transfers_total diff_count created_at',num),'period_day':string()})
obj('Issue',{**fields('id created_at',num),**fields('kind resource detail state')})
obj('Outbox',{**fields('id kind aggregate_id state last_error'),**fields('attempts next_at created_at',num)})
obj('Reconciliation',{'batches':arr(ref('ReconcileBatch')),'issues':arr(ref('Issue')),'outbox':arr(ref('Outbox'))})
obj('Audit',{**fields('id actor_id created_at',num),**fields('action resource detail')})
obj('Team',{'level1':arr(ref('Member')),'level2':arr(ref('Member')),'commissions':arr(ref('Commission'))})
obj('Config',{**fields('brand appId mchId support privacy shareBase'),**{k:boolean() for k in ['devMode','distributionEnabled','transferEnabled','monthlyEnabled','logisticsEnabled']}})
obj('Session',{'token':string('高熵会话凭据，仅客户端安全保存'),'kind':string(),'profile':{'oneOf':[ref('Member'),ref('Admin')]}})
obj('Prepay',{**fields('timeStamp nonceStr package signType paySign')})
obj('Summary',{**fields('members orders gmv pendingOrders pendingRefunds pendingWithdrawals issues',num),'trend':arr(ref('Order'))})
obj('Error',fields('code message requestId'),['code','message'])
obj('LoginRequest',fields('code inviteCode'),['code'])
obj('DevLoginRequest',fields('name inviteCode'),['name'])
obj('AdminLoginRequest',fields('username password'))
obj('QuoteRequest',{'items':arr({'type':'object','properties':{'skuId':num(),'quantity':{'type':'integer','minimum':1,'maximum':99}},'required':['skuId','quantity']}),'addressId':num(),'pointsRequested':{'type':'integer','minimum':0}})
obj('OrderRequest',{'quoteId':string(),'addressId':num(),'buyerRemark':string()},['quoteId','addressId'])
obj('RefundRequest',{'orderNo':string(),'reason':string(),'lines':arr({'type':'object','properties':{'orderItemId':num(),'quantity':{'type':'integer','minimum':1}},'required':['orderItemId','quantity']})})
obj('WithdrawRequest',{'amountFen':{'type':'integer','minimum':1000,'maximum':200000}})
obj('AccountRequest',{'username':{'type':'string','pattern':'^[a-zA-Z0-9_-]{3,40}$'},'password':{'type':'string','minLength':12},'role':{'type':'string','enum':['ADMIN','OPERATOR','FINANCE']}})
obj('Review',{'approve':boolean(),'taxFen':num('提现审批适用，须为经确认税额'),'reason':string()},['approve'])
obj('Ship',fields('carrier trackingNo'))
obj('Password',{'currentPassword':string(),'password':{'type':'string','minLength':12}})
obj('Name',{'name':{'type':'string','minLength':2,'maxLength':40}})
obj('Reason',{'reason':{'type':'string','minLength':4}})
obj('Id',{'id':{'oneOf':[string(),num()]}})
obj('StateId',{'id':string(),'state':string()})
obj('Month',{'period':{'type':'string','pattern':'^[0-9]{4}-[0-9]{2}$'}})
obj('Track',{'time':string(),'context':string()})
obj('Quantity',{'quantity':{'type':'integer','minimum':0,'maximum':99}})
obj('Accepted',{'accepted':boolean()})
obj('Blocked',{'blocked':boolean()})
obj('Active',{'active':boolean()})
obj('ResetPassword',{'password':{'type':'string','minLength':12}})
# Public projections deliberately omit cost, identity and private account fields.
obj('PublicSku',{**fields('id price stock',num),'name':string()})
obj('Invitee',{**fields('id created_at',num),'name':string(),'promoter':boolean()},['id','name','created_at'])
S['Team']['properties']['level1']=arr(ref('Invitee'))
S['Team']['properties']['level2']=arr(ref('Invitee'))
S['PublicProduct']={'type':'object','properties':{**S['Product']['properties'],'skus':arr(ref('PublicSku'))},'required':['id','name','category_id','image','status','skus']}
S['ProductCard']={'type':'object','properties':{k:v for k,v in S['Product']['properties'].items() if k!='skus'},'required':['id','name','price','stock','image']}
S['OrderSummary']={'type':'object','properties':{k:v for k,v in S['Order']['properties'].items() if k not in ['items','address','refunds']},'required':['id','member_id','state','payment_state','cash_due','created_at']}
S['Summary']['properties']['trend']=arr({'type':'object','properties':{'created_at':num(),'cash_due':num(),'state':string()},'required':['created_at','cash_due','state']})
obj('DevelopmentPrepay',{'mode':{'type':'string','const':'DEVELOPMENT'},'orderNo':string()})
S['PrepayResult']={'oneOf':[ref('Prepay'),ref('DevelopmentPrepay')]}
for model,nullable in {'Order':'transaction_id paid_at completed_at carrier tracking_no','OrderSummary':'transaction_id paid_at completed_at carrier tracking_no','Refund':'reviewer channel_id','Withdrawal':'reviewer executor channel_id package_info reason','Ledger':'order_id','Commission':'settled_at','RuleVersion':'reviewer','Settlement':'reviewer','Outbox':'last_error'}.items():
 for field in nullable.split():
  prop=S[model]['properties'][field]
  if isinstance(prop.get('type'),str):prop['type']=[prop['type'],'null']
# method,path -> (response schema, request schema)
routes={
('get','/config'):('Config',None),('get','/categories'):('[Category]',None),('get','/products'):('[ProductCard]',None),('get','/products/{id}'):('PublicProduct',None),('get','/rules/public'):('Rule',None),('post','/auth/wechat'):('Session','LoginRequest'),('post','/auth/dev'):('Session','DevLoginRequest'),('post','/auth/admin'):('Session','AdminLoginRequest'),('post','/auth/logout'):(None,None),('get','/me'):('Member',None),('put','/me'):(None,'Name'),('post','/me/promoter'):(None,'Accepted'),('get','/me/ledger'):('[Ledger]',None),('get','/me/team'):('Team',None),('get','/me/withdrawals'):('[Withdrawal]',None),('post','/me/withdrawals'):('StateId','WithdrawRequest'),('get','/me/awards'):('[Award]',None),('get','/addresses'):('[Address]',None),('post','/addresses'):('Id','Address'),('delete','/addresses/{id}'):(None,None),('get','/cart'):('[CartItem]',None),('put','/cart/{sku}'):(None,'Quantity'),('post','/checkout/quote'):('Quote','QuoteRequest'),('post','/orders'):('Order','OrderRequest'),('get','/orders'):('[Order]',None),('get','/orders/{no}'):('Order',None),('post','/orders/{no}/prepay'):('PrepayResult',None),('post','/orders/{no}/cancel'):(None,None),('get','/orders/{no}/payment-status'):('Order',None),('get','/orders/{no}/logistics'):('[Track]',None),('post','/orders/{no}/receive'):(None,None),('post','/dev/orders/{no}/pay'):(None,None),('post','/refunds'):('StateId','RefundRequest'),('get','/admin/me'):('Admin',None),('post','/admin/password'):(None,'Password'),('get','/admin/summary'):('Summary',None),('get','/admin/products'):('[Product]',None),('post','/admin/products'):('Id','Product'),('post','/admin/categories'):('Id','Category'),('get','/admin/orders'):('[OrderSummary]',None),('get','/admin/orders/{no}'):('Order',None),('post','/admin/orders/{no}/ship'):(None,'Ship'),('get','/admin/refunds'):('[Refund]',None),('post','/admin/refunds/{id}/review'):(None,'Review'),('get','/admin/members'):('[Member]',None),('post','/admin/members/{id}/block'):(None,'Blocked'),('get','/admin/commissions'):('[Commission]',None),('get','/admin/ledger'):('[Ledger]',None),('get','/admin/withdrawals'):('[Withdrawal]',None),('post','/admin/withdrawals/{id}/review'):(None,'Review'),('post','/admin/withdrawals/{id}/execute'):(None,None),('post','/admin/withdrawals/{id}/query'):(None,None),('post','/admin/dev/withdrawals/{id}/confirm'):(None,None),('get','/admin/rules'):('[RuleVersion]',None),('post','/admin/rules'):(None,'Rule'),('post','/admin/rules/{id}/approve'):(None,None),('get','/admin/settlements'):('[Settlement]',None),('post','/admin/settlements'):('Id','Month'),('post','/admin/settlements/{id}/approve'):(None,None),('get','/admin/reconciliation'):('Reconciliation',None),('post','/admin/reconciliation'):('ReconcileBatch',None),('post','/admin/issues/{id}/resolve'):(None,'Reason'),('get','/admin/accounts'):('[Admin]',None),('post','/admin/accounts'):(None,'AccountRequest'),('post','/admin/accounts/{id}/active'):(None,'Active'),('post','/admin/accounts/{id}/reset-password'):(None,'ResetPassword'),('get','/admin/audit'):('[Audit]',None)}
controller_dir=root/'server/src/main/java/com/sujian/mall/controller'
controllers=sorted(path for path in controller_dir.glob('*Controller.java') if path.name!='WechatCallbackController.java')
assert controllers,'No domain controllers found'
source='\n'.join(path.read_text() for path in controllers)
actual={(m.lower().removesuffix('mapping'),p) for m,p in re.findall(r'@(GetMapping|PostMapping|PutMapping|DeleteMapping)\("([^"]+)"\)',source)}
assert actual==set(routes),f'Undocumented/missing routes: {actual^set(routes)}'
spec={'openapi':'3.1.0','info':{'title':'素见好物交易API','version':'1.0.0','description':'金额均为整数分，积分为整数点，时间戳毫秒；管理接口需角色权限，高危操作需不同经办和复核人。'},'servers':[{'url':'/api/v1','description':'同域部署；客户端基址由环境变量覆盖'}],'paths':{},'components':{'securitySchemes':{'bearerAuth':{'type':'http','scheme':'bearer'}},'schemas':S}}
for (method,path),(response,request) in routes.items():
    data={'type':'null'} if response is None else arr(ref(response[1:-1])) if response.startswith('[') else ref(response)
    op={'operationId':method+'_'+re.sub(r'[^a-zA-Z0-9]+','_',path).strip('_'),'tags':['管理后台' if path.startswith('/admin') else '交易商城'],'summary':method.upper()+' '+path,'responses':{'200':{'description':'成功；data为业务数据，空操作为null','content':{'application/json':{'schema':{'type':'object','properties':{'code':{'type':'string','const':'OK'},'message':string(),'requestId':string(),'data':data},'required':['code','message','data','requestId']}}}},**{str(status):{'description':desc,'content':{'application/json':{'schema':ref('Error')}}} for status,desc in [(400,'字段格式错误'),(401,'登录失效'),(403,'角色权限不足'),(404,'记录不存在或无权访问'),(409,'状态、余额、库存、幂等或规则冲突'),(429,'操作限流'),(503,'配置缺失或渠道结果待确认')]}},'security':[] if path.startswith('/auth/') or path in ['/config','/categories','/products','/products/{id}','/rules/public'] else [{'bearerAuth':[]}]}
    parameters=[]
    for p in re.findall(r'\{(\w+)\}',path):parameters.append({'name':p,'in':'path','required':True,'schema':num() if p=='sku' or (p=='id' and any(x in path for x in ['products','addresses','members','rules','settlements','issues'])) else string()})
    if method=='post' and path in ['/orders','/refunds','/me/withdrawals']:parameters.append({'name':'Idempotency-Key','in':'header','required':True,'description':'8至80位，同会员同操作同键同内容返回原响应，内容改变409','schema':{'type':'string','minLength':8,'maxLength':80}})
    if method=='get' and path=='/products':parameters.extend([{'name':p,'in':'query','schema':string()} for p in ['category','search']])
    if parameters:op['parameters']=parameters
    if request:op['requestBody']={'required':True,'content':{'application/json':{'schema':ref(request)}}}
    if '/dev/' in path or path=='/auth/dev':op['description']='仅APP_DEV_MODE=true可调用。禁止用于生产资金事实。'
    if path=='/orders/{no}/prepay':op['description']='微信原生支付参数。开发环境返回mode=DEVELOPMENT和orderNo，不含真实支付参数。'
    spec['paths'].setdefault(path,{})[method]=op
for kind in ['pay','refund','transfer']:
    spec['paths']['/callbacks/wechat/'+kind]={'post':{'operationId':'wechat_'+kind,'summary':'微信签名加密回调','tags':['微信回调'],'security':[],'parameters':[{'name':name,'in':'header','required':True,'schema':string()} for name in ['Wechatpay-Serial','Wechatpay-Timestamp','Wechatpay-Nonce','Wechatpay-Signature']],'requestBody':{'required':True,'content':{'application/json':{'schema':{'type':'object','properties':{'id':string(),'event_type':string(),'resource':{'type':'object','properties':{'algorithm':{'type':'string','const':'AEAD_AES_256_GCM'},'nonce':string(),'ciphertext':string(),'associated_data':string()},'required':['algorithm','nonce','ciphertext']}},'required':['id','resource']}}}},'responses':{'200':{'description':'仅验签、解密和业务核验成功后返回SUCCESS'},'400':{'description':'签名或密文无效'},'409':{'description':'金额、归属或业务状态冲突'}}}}
text=json.dumps(spec,ensure_ascii=False,indent=2)+'\n'
(root/'docs/openapi.json').write_text(text)
static=root/'server/src/main/resources/static';static.mkdir(exist_ok=True)
(static/'openapi.json').write_text(text)
print(f'Contract: {len(routes)} application operations + 3 WeChat callbacks')

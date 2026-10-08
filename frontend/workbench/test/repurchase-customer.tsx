// UTF-8. Test-only transport for actual customer preflight and order components.
import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import ExternalRepurchasePage from '../src/pages/ExternalRepurchasePage'
import { api } from '../src/services/api'
const fixture={status:'EXISTING_CUSTOMER',fail:false,submitFail:false,delay:0,checks:[] as unknown[],orders:[] as unknown[]}
Object.assign(window,{repurchaseFixture:fixture})
Object.assign(api,{
 checkRepurchaseCustomer:async(payload:any)=>{fixture.checks.push(payload);const status=fixture.status;await new Promise(r=>setTimeout(r,fixture.delay));if(fixture.fail)throw new Error('模拟网络错误');return {matchStatus:status,canRepurchase:['EXISTING_CUSTOMER','NO_MATCH'].includes(status),reason:status==='EXISTING_CUSTOMER'?'已识别客户，本次订单归属当前录单人':status==='NO_MATCH'?'未找到系统客户，将创建客户主档':'身份冲突或客户暂不可复购',personId:status==='EXISTING_CUSTOMER'?10:undefined,customerName:payload.customerName,maskedMobile:'138****8000'}},
 areaTree:async()=>[{id:990000000,name:'其他省份',selectionCode:'OTHER',children:[{id:990000001,name:'其他城市',selectionCode:'OTHER',children:[]}]}],
 salesOrderCatalog:async()=>({categoryTree:[{id:1,name:'测试分类',children:[]}],spus:[{spuRef:'p1',spuName:'测试课程',categoryId:1,categoryPath:[{id:1,name:'测试分类'}],attrs:[]}],skus:[{spuRef:'p1',skuRef:'s1',skuName:'测试方案',price:100,attrValues:{},spuName:'测试课程'}]}),
 giftConfigList:async()=>[],dictDataByType:async()=>[{value:'test',label:'测试选项',status:0}],
 currentPurchaseIntent:async()=>undefined,
 savePurchaseIntentDraft:async(payload:any)=>({id:55,collectionMode:payload.collectionMode,purchaseType:payload.purchaseType,personId:10,version:1,paymentLocked:false}),
 uploadSalesOrderVoucher:async()=>({infraFileId:100,originalName:'voucher.pdf',fileUrl:'https://example.test/voucher.pdf',contentType:'application/pdf'}),
 submitCustomerRepurchase:async(payload:any)=>{fixture.orders.push(payload);if(fixture.submitFail)throw new Error('模拟提交失败');return 99},
 salesOrder:async()=>({id:99,orderNo:'OD-TEST',orderType:'repurchase'})
})
createRoot(document.getElementById('root')!).render(<ThemeProvider><App><BrowserRouter><main style={{padding:16}}><ExternalRepurchasePage permissions={location.search.includes('denied') ? [] : ['zsjos:sales-order:create']}/></main></BrowserRouter></App></ThemeProvider>)

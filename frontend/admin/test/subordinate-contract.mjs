import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import assert from 'node:assert/strict'
const calls=[]
const request={get:async x=>(calls.push(x),[]),post:async x=>(calls.push(x),{successCount:0,failureCount:1,items:[{success:false,message:'范围拒绝'}]})}
const exports={}
vm.runInNewContext(ts.transpileModule(readFileSync('src/api/zsjos/workbenchMenus.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,{exports,require:()=>({__esModule:true,default:request})})
await exports.subordinateBatchTransferCandidates([1,2])
assert.equal(calls[0].url,'/zsjos/subordinate-sales/batch-transfer-candidates')
assert.equal(calls[0].params.leadIds,'1,2')
const data={leadIds:[1],targetUserId:2,reason:'fixture',idempotencyKey:'fixture-key'}
const result=await exports.batchTransferSubordinateLeads(data)
assert.equal(calls[1].url,'/zsjos/subordinate-sales/leads/batch-transfer')
assert.equal(calls[1].data,data)
assert.equal(result.failureCount,1)
await exports.batchReleaseSubordinateLeads(data)
assert.equal(calls[2].url,'/zsjos/subordinate-sales/leads/batch-release-public-sea')
console.log('PASS Vue API candidate, transfer/release paths, idempotency payload and partial failures')


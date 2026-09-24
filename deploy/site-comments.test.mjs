import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createCommentStore, installSiteComments } from './site-comments.mjs';
const a={id:'alice',username:'Alice'}, b={id:'bob',username:'Bob'};
test('comments enforce authentication, ownership, pagination, reply resource and tombstones',()=>{
 const store=createCommentStore(':memory:');
 try {
  assert.throws(()=>store.add('netease:1','hi',null,null),e=>e.status===401);
  assert.throws(()=>store.add('../bad','hi',null,a),e=>e.status===400);
  const first=store.add('netease:1','hello',null,a).comment;
  assert.throws(()=>store.remove(first.id,b),e=>e.status===403);
  assert.throws(()=>store.add('netease:2','bad',first.id,b),e=>e.status===404);
  store.add('netease:1','reply',first.id,b);
  assert.equal(store.list('netease:1',{parent:first.id}).total,1);
  assert.equal(store.list('netease:1').comments[0].replies,1);
  assert.equal(store.like(first.id,true,b).comment.likes,1);
  assert.equal(store.like(first.id,true,b).comment.likes,1);
  assert.equal(store.like(first.id,false,b).comment.likes,0);
  store.remove(first.id,a);
  assert.equal(store.list('netease:1').comments[0].content,'');
  assert.equal(store.list('netease:1').comments[0].deleted,true);
  assert.equal(store.list('netease:1',{parent:first.id}).total,1);
  assert.throws(()=>store.like(first.id,true,b),e=>e.status===404);
 } finally { store.close(); }
});
test('records persist through restart and song providers remain isolated',()=>{
 const dir=mkdtempSync(join(tmpdir(),'site-comments-'));const path=join(dir,'db.sqlite');
 try {
  let store=createCommentStore(path);store.add('netease:123','first',null,a);store.add('kugou:'+'a'.repeat(32),'second',null,b);store.close();
  store=createCommentStore(path);assert.equal(store.list('netease:123').total,1);assert.equal(store.list('kugou:'+'a'.repeat(32)).total,1);store.close();
 } finally {rmSync(dir,{recursive:true,force:true});}
});
test('invalid content and excessive comments fail without partial writes',()=>{
 const store=createCommentStore(':memory:',{now:()=>100000});
 try {assert.throws(()=>store.add('netease:1',' '.repeat(5),null,a));
  for(let i=0;i<5;i++)store.add('netease:1','item '+i,null,a);
  assert.throws(()=>store.add('netease:1','rate limited',null,a),e=>e.status===429);
  assert.equal(store.list('netease:1',{limit:2}).more,true);assert.equal(store.list('netease:1').total,5);
 } finally {store.close();}
});

test('write routes verify bearer instead of accepting a stale browser session',()=>{
 const dir=mkdtempSync(join(tmpdir(),'site-comments-routes-')); const routes=new Map();
 const app={get:(p,...h)=>routes.set('GET '+p,h.at(-1)),post:(p,...h)=>routes.set('POST '+p,h.at(-1)),delete:(p,...h)=>routes.set('DELETE '+p,h.at(-1))};
 const store=installSiteComments(app,{dataRoot:dir,jsonParser:()=>{},resolveUser:req=>req.session?.user?{user:req.session.user}:req.headers?.authorization==='Bearer valid'?{user:a}:null});
 try {
  function request(token) { let code=200;let body;const req={headers:{authorization:token},session:{user:a},get:()=>token,body:{song:'netease:1',content:'hello'}};
   const res={set:()=>res,status:c=>{code=c;return res},json:b=>{body=b}};routes.get('POST /comments')(req,res);return {code,body}; }
  assert.equal(request('Bearer invalid').code,401);assert.equal(store.list('netease:1').total,0);
  assert.equal(request('Bearer valid').code,200);assert.equal(store.list('netease:1').total,1);
  let visible;const response={set:()=>response,json:b=>{visible=b},status:()=>response};
  routes.get('GET /comments')({headers:{authorization:'Bearer valid'},get:()=> 'Bearer valid',session:{user:b},query:{song:'netease:1'}},response);
  assert.equal(visible.comments[0].owned,true);

  assert.doesNotThrow(()=>store.list('netease:1',{offset:'0.5',limit:'2.4'}));
 } finally {store.close();rmSync(dir,{recursive:true,force:true});}
});

test('nested replies retain their exact target inside the root thread',()=>{
 const store=createCommentStore(':memory:');
 try {const root=store.add('netease:1','root',null,a).comment;const reply=store.add('netease:1','reply',root.id,b).comment;
  const nested=store.add('netease:1','nested',reply.id,a).comment;assert.equal(nested.parentId,root.id);assert.equal(nested.replyTo,reply.id);assert.equal(nested.quotedContent,'reply');
  assert.equal(store.list('netease:1',{parent:root.id}).total,2);store.remove(reply.id,b);
  assert.equal(store.list('netease:1',{parent:root.id}).comments.find(c=>c.id===nested.id).quotedContent,'该评论已删除');
 } finally {store.close();}
});

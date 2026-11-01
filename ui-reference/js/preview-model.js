// Isolated in-memory companion. All preview data resets on reload.
export const previewModel={
  categories:[],
  sources:[],
  allocations:new Map(),
  expenses:[],
  nextExpenseId:1,
  nextCategoryId:1,
  nextSourceId:1
};

import {periodKey,parseCivilDate} from "./budget-period.js";

export function amountFor(date,sourceId){
  return previewModel.allocations.get(periodKey(date))?.get(sourceId)??0;
}

export function totalFor(date){
  return [...(previewModel.allocations.get(periodKey(date))?.values()??[])].reduce((a,b)=>a+b,0);
}

export function parseAmount(text){
  const value=text.trim().replace(",",".");
  if(!/^(?:\d+(?:\.\d{1,2})?|\.\d{1,2})$/.test(value))return null;
  const [whole,fraction=""]=value.split(".");
  const cents=BigInt(whole||"0")*100n+BigInt(fraction.padEnd(2,"0"));
  return cents<=BigInt(Number.MAX_SAFE_INTEGER)?Number(cents):null;
}

function safe(value){
  if(value>BigInt(Number.MAX_SAFE_INTEGER)||value<BigInt(Number.MIN_SAFE_INTEGER))throw new RangeError("overflow");
  return Number(value);
}
export function snapshotFor(date,expenses=previewModel.expenses){
  const key=periodKey(date);
  const selected=expenses.filter(item=>periodKey(parseCivilDate(item.date))===key)
    .sort((a,b)=>b.date.localeCompare(a.date)||a.id.localeCompare(b.id));
  const spent=safe(selected.reduce((sum,item)=>sum+BigInt(item.amountMinor),0n));
  const allocated=totalFor(date);
  const sources=previewModel.sources.map(source=>{
    const sourceSpent=safe(selected.filter(item=>item.sourceId===source.id).reduce((sum,item)=>sum+BigInt(item.amountMinor),0n));
    return {source,spent:sourceSpent,expenseCount:selected.filter(item=>item.sourceId===source.id).length,remaining:safe(BigInt(amountFor(date,source.id))-BigInt(sourceSpent))};
  });
  const bySpending=(a,b)=>a.spent===b.spent?(a.id===b.id?0:a.id===null?-1:b.id===null?1:a.id<b.id?-1:1):a.spent>b.spent?-1:1;
  const categoryBreakdown=[...new Set(selected.map(item=>item.categoryId))].map(id=>{
    const rows=selected.filter(item=>item.categoryId===id);
    return {id,name:previewModel.categories.find(item=>item.id===id)?.name??null,
      spent:safe(rows.reduce((sum,item)=>sum+BigInt(item.amountMinor),0n)),expenseCount:rows.length};
  }).sort(bySpending);
  const sourceBreakdown=sources.filter(item=>item.expenseCount>0).map(item=>({
    id:item.source.id,name:item.source.name,spent:item.spent,expenseCount:item.expenseCount
  })).sort(bySpending);
  return {expenses:selected,spent,allocated,remaining:safe(BigInt(allocated)-BigInt(spent)),sources,categoryBreakdown,sourceBreakdown};
}
export function createExpense(input){
  const date=parseCivilDate(input.date);
  if(!Number.isSafeInteger(input.amountMinor)||input.amountMinor<=0||!date||
    !previewModel.sources.some(item=>item.id===input.sourceId)||
    (input.categoryId!=null&&!previewModel.categories.some(item=>item.id===input.categoryId)))return null;
  const expense={id:`expense-${previewModel.nextExpenseId}`,amountMinor:input.amountMinor,
    date:input.date,sourceId:input.sourceId,categoryId:input.categoryId??null,note:input.note||null};
  try{snapshotFor(date,[...previewModel.expenses,expense])}catch{return null}
  previewModel.expenses.push(expense);previewModel.nextExpenseId++;
  return expense;
}
export function correctExpenseAmount(id,amountMinor){
  const original=previewModel.expenses.find(item=>item.id===id);
  if(!original||!Number.isSafeInteger(amountMinor)||amountMinor<=0)return false;
  const candidate=previewModel.expenses.map(item=>item.id===id?{...item,amountMinor}:item);
  try{snapshotFor(parseCivilDate(original.date),candidate)}catch{return false}
  original.amountMinor=amountMinor;
  return true;
}

export function addCategory(name){
  const clean=name.trim();
  if(!clean)return null;
  const category={id:`category-${previewModel.nextCategoryId++}`,name:clean};
  previewModel.categories.push(category);
  return category;
}

export function addSource(name,date,initial){
  const clean=name.trim();
  if(!clean||!Number.isSafeInteger(initial)||initial<0)return null;
  if(!Number.isSafeInteger(totalFor(date)+initial))return null;
  const source={id:`source-${previewModel.nextSourceId++}`,name:clean};
  previewModel.sources.push(source);
  const key=periodKey(date);
  const allocations=previewModel.allocations.get(key)??new Map();
  allocations.set(source.id,initial);
  previewModel.allocations.set(key,allocations);
  return source;
}

export function previewAdjustment(date,sourceId,delta){
  if(!previewModel.sources.some(source=>source.id===sourceId))return null;
  const result=amountFor(date,sourceId)+delta;
  return Number.isSafeInteger(result)?result:null;
}

export function updateSource(sourceId,name,date,delta){
  const source=previewModel.sources.find(item=>item.id===sourceId);
  const clean=name.trim();
  const result=previewAdjustment(date,sourceId,delta);
  if(!source||!clean||result===null||result<0)return false;
  const nextTotal=totalFor(date)-amountFor(date,sourceId)+result;
  if(!Number.isSafeInteger(nextTotal))return false;
  source.name=clean;
  const key=periodKey(date);
  const allocations=previewModel.allocations.get(key)??new Map();
  allocations.set(sourceId,result);
  previewModel.allocations.set(key,allocations);
  return true;
}

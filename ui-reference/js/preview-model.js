// Temporary companion to the Android ViewModel. No disk storage or expense mutation.
export const previewModel={
  categories:[],
  sources:[],
  allocations:new Map(),
  expenseTotal:0,
  nextCategoryId:1,
  nextSourceId:1
};

import {periodKey} from "./budget-period.js";

export function amountFor(date,sourceId){
  return previewModel.allocations.get(periodKey(date))?.get(sourceId)??0;
}

export function totalFor(date){
  return [...(previewModel.allocations.get(periodKey(date))?.values()??[])].reduce((a,b)=>a+b,0);
}

export function parseAmount(text){
  const value=text.trim().replace(",",".");
  if(!/^(?:\d+(?:\.\d{1,2})?|\.\d{1,2})$/.test(value))return null;
  const cents=Math.round(Number(value)*100);
  return Number.isSafeInteger(cents)?cents:null;
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

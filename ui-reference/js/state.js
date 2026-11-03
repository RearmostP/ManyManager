import {periodFor,parseCivilDate} from "./dates.js";
import {demo} from "./demo.js";

export const state={
  page:0,tab:0,language:"he",theme:"light",overlay:null,
  history:[],expanded:new Set(),budgetExpanded:false,
  currentDate:parseCivilDate(demo.today),budgetMonth:periodFor(parseCivilDate(demo.today)).start,
  nextId:8,
  expenseDraft:{amount:"",date:"",note:"",categoryId:null,sourceId:null},
  expenseEditId:null,expandedSummaryGroups:new Set(),expenseEditing:false,
  categoryDraft:"",categoryReturnOverlay:null,
  sourceDraft:{name:"",initial:"0",adjustment:"",operation:"add",sourceId:null},
  dialogMonth:null
};
export function changePage(index,scrollElement){
  if(index===state.page)return;
  if(index===3)state.budgetMonth=periodFor(state.currentDate).start;
  if(index===0)state.history=[];
  else state.history.push({page:state.page,tab:state.tab,scroll:scrollElement.scrollTop});
  state.page=index;state.budgetExpanded=false;scrollElement.scrollTop=0;
}
export function savedExpenseDraft(item){
  return item?{amount:`${Math.trunc(item.amountMinor/100)}.${String(item.amountMinor%100).padStart(2,"0")}`,
    date:item.date,categoryId:item.categoryId,sourceId:item.sourceId,note:item.note??""}:
    {amount:"",date:"",categoryId:null,sourceId:null,note:""};
}
export function cancelExpenseEdit(){
  state.expenseEditing=false;
  state.expenseDraft=savedExpenseDraft(demo.expenses.find(item=>item.id===state.expenseEditId));
}
export function closeOverlay(){
  if(state.overlay==="add-category"){
    state.overlay=state.categoryReturnOverlay;
    state.categoryReturnOverlay=null;
  }else if(state.overlay==="delete-expense")state.overlay="expense-details";
  else if(state.overlay==="expense-details"&&state.expenseEditing)cancelExpenseEdit();
  else state.overlay=null;
}
export function goBack(scrollElement){
  if(state.overlay){closeOverlay();return true}
  if(state.page===0&&state.budgetExpanded){state.budgetExpanded=false;return true}
  if(state.page===0)return false;
  const previous=state.history.pop()||{page:0,tab:0,scroll:0};
  state.page=previous.page;state.tab=previous.tab;
  requestAnimationFrame(()=>scrollElement.scrollTop=previous.scroll);
  return true;
}

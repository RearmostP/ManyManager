import {adjacentPeriod,civilDate,periodKey} from "./dates.js";
import {state,changePage,goBack,closeOverlay,cancelExpenseEdit,savedExpenseDraft} from "./state.js";
import {loadLanguage,t} from "./i18n.js";
import {icon} from "./icons.js";
import {demo,parseAmount,amountFor,totalFor} from "./demo.js";
import {home} from "./screens/home.js";
import {summary} from "./screens/summary.js";
import {budget} from "./screens/budget.js";
import {settings} from "./screens/settings.js";
import {categories} from "./screens/categories.js";
import {drawer} from "./components/drawer.js";
import {expense,canSaveExpense} from "./components/expense-sheet.js";
import {expenseDelete} from "./components/expense-delete.js";
import {expenseDetails,expenseDetailsBody} from "./components/expense-details.js";
import {categoryDialog,beginCategoryCreation,confirmCategoryCreation} from "./components/category-dialog.js";
import {sourceDialog} from "./components/source-dialog.js";
import {categoryPicker} from "./components/category-picker.js";

const app=document.getElementById("app");
const page=document.getElementById("page");
const scroll=document.getElementById("page-scroll");
const overlay=document.getElementById("overlay-root");
const menuButton=document.getElementById("menu-button");

function render(){
  const top=scroll.scrollTop;
  document.documentElement.lang=state.language;
  document.documentElement.dir=state.language==="he"?"rtl":"ltr";
  document.documentElement.dataset.theme=state.theme;
  document.title=t(state.language,"app_name");
  menuButton.setAttribute("aria-label",t(state.language,"menu_button_label"));
  menuButton.innerHTML=icon("menu");
  page.innerHTML=[home,summary,settings,budget,categories][state.page]();
  if(["expense-details","delete-expense"].includes(state.overlay)||(state.overlay==="category-picker"&&state.pickerReturnOverlay==="expense-details")){
    // Keep the details panel in place when switching between read/edit/confirmation.
    if(!overlay.querySelector("#expense-details"))overlay.innerHTML=expenseDetails();
    const panel=overlay.querySelector("#expense-details");
    const focused=document.activeElement,focusId=panel.contains(focused)?focused.id:null;
    panel.innerHTML=expenseDetailsBody();
    panel.inert=state.overlay!=="expense-details";
    if(panel.inert)panel.setAttribute("aria-hidden","true");else panel.removeAttribute("aria-hidden");
    overlay.querySelector("[data-expense-delete-confirmation]")?.remove();
    if(panel.inert){
      const confirmation=document.createElement("div");
      confirmation.dataset.expenseDeleteConfirmation="";
      confirmation.innerHTML=state.overlay==="category-picker"?categoryPicker():expenseDelete();
      overlay.append(confirmation);
    }else if(focusId)panel.querySelector("#"+CSS.escape(focusId))?.focus({preventScroll:true});
  }else overlay.innerHTML=({
    menu:drawer,expense,"add-category":categoryDialog,"category-picker":categoryPicker,
    "add-source":sourceDialog,"edit-source":sourceDialog,"overall-budget":sourceDialog
  })[state.overlay]?.()??"";
  app.inert=Boolean(state.overlay);
  scroll.style.overflow=state.overlay?"hidden":"auto";
  scroll.scrollTop=top;
}

// These handlers only change disposable examples. There are no async writes or observers.
document.addEventListener("input",event=>{
  const field=event.target;
  const expenseKey=field.dataset.details||field.dataset.expense;
  if(expenseKey){
    state.expenseDraft[expenseKey]=["categoryId","sourceId"].includes(expenseKey)?(field.value||null):field.value;
    const save=overlay.querySelector('[data-action="save-expense"],[data-action="save-expense-details"]');
    if(save)save.disabled=!canSaveExpense();
  }
  if(field.hasAttribute("data-category-draft"))state.categoryDraft=field.value;
  if(field.dataset.sourceDraft){
    state.sourceDraft[field.dataset.sourceDraft]=field.value;
  }
});

document.addEventListener("click",async event=>{
  const button=event.target.closest("[data-action]");
  if(!button||(app.inert&&app.contains(button)))return;
  if(button.classList.contains("scrim")&&event.target.closest("[data-stop-close]"))return;
  const saved=()=>demo.expenses.find(item=>item.id===state.expenseEditId);
  switch(button.dataset.action){
    case "close-overlay": closeOverlay(); break;
    case "navigate": changePage(Number(button.dataset.index),scroll);state.overlay=null;break;
    case "back": goBack(scroll);break;
    case "add-category": beginCategoryCreation();break;
    case "confirm-category": confirmCategoryCreation();break;
    case "overall-budget":
    case "add-source":
    case "edit-source":{
      const source=demo.sources.find(item=>item.id===button.dataset.id);
      state.dialogMonth=new Date(state.budgetMonth);
      const amount=button.dataset.action==="overall-budget"?totalFor(state.dialogMonth):source?amountFor(state.dialogMonth,source.id):null;
      state.sourceDraft={name:source?.name??"",initial:amount==null?"":String(amount/100),sourceId:source?.id};
      state.overlay=button.dataset.action;break;
    }
    case "confirm-add-source":{
      const id="s"+state.nextId++;
      demo.sources.push({id,name:state.sourceDraft.name||"Example source"});
      demo.allocations[id]=state.sourceDraft.initial.trim()===""?{}:{[periodKey(state.dialogMonth)]:parseAmount(state.sourceDraft.initial)};
      state.overlay=null;break;
    }
    case "confirm-edit-source":{
      const draft=state.sourceDraft;
      const source=demo.sources.find(item=>item.id===draft.sourceId);
      source.name=draft.name||source.name;
      const key=periodKey(state.dialogMonth);
      if(draft.initial.trim()==="")delete demo.allocations[source.id][key];
      else demo.allocations[source.id][key]=parseAmount(draft.initial);
      state.overlay=null;break;
    }
    case "confirm-overall":{
      const key=periodKey(state.dialogMonth);
      if(state.sourceDraft.initial.trim()==="")delete demo.budgets[key];
      else demo.budgets[key]=parseAmount(state.sourceDraft.initial);
      state.overlay=null;break;
    }
    case "remove-threshold":{
      const key=periodKey(state.dialogMonth);
      if(state.overlay==="overall-budget")delete demo.budgets[key];
      else delete demo.allocations[state.sourceDraft.sourceId][key];
      state.overlay=null;break;
    }
    case "open-category-picker":
      state.pickerReturnOverlay=state.overlay;state.overlay="category-picker";break;
    case "select-category":
      state.expenseDraft.categoryId=button.dataset.id||null;closeOverlay();break;
    case "expense-category": state.expenseDraft.categoryId=button.dataset.id??null;break;
    case "expense-source": state.expenseDraft.sourceId=button.dataset.id;break;
    case "expense":
      state.expenseEditId=null;state.expenseEditing=false;
      state.expenseDraft={amount:"",date:civilDate(state.currentDate),note:"",categoryId:null,sourceId:null};
      state.overlay="expense";break;
    case "save-expense":
      if(!canSaveExpense())return;
      demo.expenses.push({...state.expenseDraft,id:"e"+state.nextId++,amountMinor:parseAmount(state.expenseDraft.amount)});
      state.overlay=null;break;
    case "view-expense":
      state.expenseEditId=button.dataset.id;
      state.expenseEditing=false;state.expenseDraft=savedExpenseDraft(saved());
      state.overlay="expense-details";break;
    case "begin-expense-edit":
      state.expenseDraft=savedExpenseDraft(saved());state.expenseEditing=true;break;
    case "cancel-expense-edit": cancelExpenseEdit();break;
    case "save-expense-details":
      if(!canSaveExpense())return;
      Object.assign(saved(),state.expenseDraft,{amountMinor:parseAmount(state.expenseDraft.amount)});
      cancelExpenseEdit();break;
    case "delete-expense": state.overlay="delete-expense";break;
    case "confirm-delete-expense":
      demo.expenses=demo.expenses.filter(item=>item.id!==state.expenseEditId);
      state.overlay=null;break;
    case "expense-group":{
      const key=button.dataset.kind+":"+(button.dataset.id||"");
      state.expandedSummaryGroups.has(key)?state.expandedSummaryGroups.delete(key):state.expandedSummaryGroups.add(key);
      render();
      overlay.querySelector("button")?.focus({preventScroll:true});
      page.querySelector(`[data-action="expense-group"][data-kind="${button.dataset.kind}"][data-id="${button.dataset.id}"]`)?.focus({preventScroll:true});
      return;
    }
    case "budget-toggle": state.budgetExpanded=!state.budgetExpanded;break;
    case "tab": state.tab=Number(button.dataset.index);break;
    case "budget-prev":
    case "budget-next": state.budgetMonth=adjacentPeriod(state.budgetMonth,button.dataset.action==="budget-prev"?-1:1);break;
    case "toggle-setting":{
      const key=button.dataset.key;
      state.expanded.has(key)?state.expanded.delete(key):state.expanded.add(key);break;
    }
    case "language":
      await loadLanguage(button.dataset.value);state.language=button.dataset.value;break;
    case "theme": state.theme=button.dataset.value;break;
    default:return;
  }
  render();
});
menuButton.addEventListener("click",()=>{state.overlay="menu";render()});
document.addEventListener("keydown",event=>{
  if(event.key==="Escape"&&goBack(scroll)){event.preventDefault();render()}
});

// original's pointer lifecycle; still only a disposable visual gesture demonstration.
let drag=null;
overlay.addEventListener("pointerdown",event=>{
  const handle=event.target.closest(".drag-area");
  if(!handle||state.overlay!=="expense"||drag)return;
  const sheet=handle.closest(".sheet");
  drag={pointerId:event.pointerId,startY:event.clientY,distance:0,handle,sheet};
  handle.setPointerCapture(event.pointerId);
});
overlay.addEventListener("pointermove",event=>{
  if(!drag||event.pointerId!==drag.pointerId)return;
  drag.distance=Math.max(0,event.clientY-drag.startY);
  drag.sheet.style.transform=`translateY(${drag.distance}px)`;
});
function finishDrag(event,cancelled){
  if(!drag||event.pointerId!==drag.pointerId)return;
  const {handle,sheet,distance,pointerId}=drag;
  drag=null;
  if(handle.hasPointerCapture(pointerId))handle.releasePointerCapture(pointerId);
  if(!cancelled&&distance>=64){state.overlay=null;render()}
  else{
    sheet.style.transition="transform 180ms ease";
    sheet.style.transform="translateY(0)";
  }
}
overlay.addEventListener("pointerup",event=>finishDrag(event,false));
overlay.addEventListener("pointercancel",event=>finishDrag(event,true));
await loadLanguage(state.language);
render();

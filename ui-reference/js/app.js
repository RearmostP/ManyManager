import {adjacentPeriod,civilDate,periodKey} from "./dates.js";
import {state,changePage,goBack,closeOverlay,cancelExpenseEdit,savedExpenseDraft} from "./state.js";
import {loadLanguage,t} from "./i18n.js";
import {icon} from "./icons.js";
import {demo,parseAmount} from "./demo.js";
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
import {sourceDialog,sourcePreview} from "./components/source-dialog.js";

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
  if(["expense-details","delete-expense"].includes(state.overlay)){
    // Keep the details panel in place when switching between read/edit/confirmation.
    if(!overlay.querySelector("#expense-details"))overlay.innerHTML=expenseDetails();
    const panel=overlay.querySelector("#expense-details");
    panel.innerHTML=expenseDetailsBody();
    panel.inert=state.overlay==="delete-expense";
    overlay.querySelector("[data-expense-delete-confirmation]")?.remove();
    if(panel.inert){
      const confirmation=document.createElement("div");
      confirmation.dataset.expenseDeleteConfirmation="";
      confirmation.innerHTML=expenseDelete();
      overlay.append(confirmation);
    }
  }else overlay.innerHTML=({
    menu:drawer,expense,"add-category":categoryDialog,
    "add-source":sourceDialog,"edit-source":sourceDialog
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
    const preview=document.getElementById("source-preview");
    if(preview)preview.textContent=sourcePreview();
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
    case "add-source":
    case "edit-source":{
      const source=demo.sources.find(item=>item.id===button.dataset.id);
      state.dialogMonth=new Date(state.budgetMonth);
      state.sourceDraft={name:source?.name??"",initial:"0",adjustment:"",operation:"add",sourceId:source?.id};
      state.overlay=button.dataset.action;break;
    }
    case "confirm-add-source":{
      const id="s"+state.nextId++;
      demo.sources.push({id,name:state.sourceDraft.name||"Example source"});
      demo.allocations[id]={[periodKey(state.dialogMonth)]:parseAmount(state.sourceDraft.initial)};
      state.overlay=null;break;
    }
    case "confirm-edit-source":{
      const draft=state.sourceDraft;
      const source=demo.sources.find(item=>item.id===draft.sourceId);
      source.name=draft.name||source.name;
      const key=periodKey(state.dialogMonth),amount=parseAmount(draft.adjustment);
      demo.allocations[source.id][key]=(demo.allocations[source.id][key]??0)+(draft.operation==="add"?amount:-amount);
      state.overlay=null;break;
    }
    case "operation": state.sourceDraft.operation=button.dataset.value;break;
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

// A small visual gesture demo for the existing Add Expense sheet.
let drag;
overlay.addEventListener("pointerdown",event=>{
  const handle=event.target.closest(".drag-area");
  if(!handle)return;
  drag={handle,sheet:handle.closest(".sheet"),start:event.clientY};
  handle.setPointerCapture(event.pointerId);
});
overlay.addEventListener("pointermove",event=>{
  if(drag)drag.sheet.style.transform=`translateY(${Math.max(0,event.clientY-drag.start)}px)`;
});
function endDrag(event){
  if(!drag)return;
  if(event.type!=="pointercancel"&&event.clientY-drag.start>=64){state.overlay=null;render()}
  else drag.sheet.style.transform="";
  drag=null;
}
overlay.addEventListener("pointerup",endDrag);
overlay.addEventListener("pointercancel",endDrag);
await loadLanguage(state.language);
render();

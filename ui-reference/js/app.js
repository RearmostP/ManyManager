import {state,changePage,goBack,closeOverlay} from "./state.js";
import {loadLanguage,t,shortDate} from "./i18n.js";
import {icon} from "./icons.js";
import {previewModel,addSource,updateSource,parseAmount} from "./preview-model.js";
import {home} from "./screens/home.js";
import {summary} from "./screens/summary.js";
import {budget} from "./screens/budget.js";
import {settings} from "./screens/settings.js";
import {categories} from "./screens/categories.js";
import {drawer} from "./components/drawer.js";
import {expense} from "./components/expense-sheet.js";
import {categoryDialog,beginCategoryCreation,confirmCategoryCreation} from "./components/category-dialog.js";
import {sourceDialog,sourcePreview} from "./components/source-dialog.js";

const app=document.getElementById("app");
const page=document.getElementById("page");
const scroll=document.getElementById("page-scroll");
const overlay=document.getElementById("overlay-root");
const menuButton=document.getElementById("menu-button");
const translate=key=>t(state.language,key);

function render(){
  document.documentElement.lang=state.language;
  document.documentElement.dir=state.language==="he"?"rtl":"ltr";
  document.documentElement.dataset.theme=state.theme;
  document.title=translate("app_name");
  menuButton.setAttribute("aria-label",translate("menu_button_label"));
  menuButton.innerHTML=icon("menu");
  page.innerHTML=[home,summary,settings,budget,categories][state.page]();
  overlay.innerHTML=({
    menu:drawer,expense, "add-category":categoryDialog,
    "add-source":sourceDialog,"edit-source":sourceDialog
  })[state.overlay]?.()??"";
  app.inert=Boolean(state.overlay);
  scroll.style.overflow=state.overlay?"hidden":"auto";
}
function renderKeepScroll(){
  const top=scroll.scrollTop;
  render();
  scroll.scrollTop=top;
}
function showError(id,key){
  const element=document.getElementById(id);
  element.textContent=translate(key);
  element.hidden=false;
}
function clearError(id){
  const element=document.getElementById(id);
  if(element)element.hidden=true;
}
await loadLanguage(state.language);
render();

document.addEventListener("input",event=>{
  if(event.target.dataset.expense)state.expenseDraft[event.target.dataset.expense]=event.target.value;
  if(event.target.hasAttribute("data-category-draft")){
    state.categoryDraft=event.target.value;
    clearError("category-error");
  }
  if(event.target.dataset.sourceDraft){
    state.sourceDraft[event.target.dataset.sourceDraft]=event.target.value;
    clearError("source-error");
    if(state.overlay==="edit-source"){
      document.getElementById("source-preview").textContent=sourcePreview();
    }
  }
});
document.addEventListener("click",async event=>{
  const button=event.target.closest("[data-action]");
  if(!button)return;
  if(button.classList.contains("scrim")&&event.target.closest("[data-stop-close]"))return;
  switch(button.dataset.action){
    case "close-overlay":
      closeOverlay();renderKeepScroll();break;
    case "navigate":
      changePage(Number(button.dataset.index),scroll);state.overlay=null;render();break;
    case "back":
      if(goBack(scroll))renderKeepScroll();break;
    case "add-category":
      beginCategoryCreation();renderKeepScroll();break;
    case "confirm-category":
      if(!confirmCategoryCreation()){showError("category-error","category_name_required");break}
      renderKeepScroll();break;
    case "add-source":
      state.dialogMonth=new Date(state.budgetMonth);
      state.sourceDraft={name:"",initial:"0",adjustment:"",operation:"add",sourceId:null};
      state.overlay="add-source";renderKeepScroll();break;
    case "edit-source":{
      const source=previewModel.sources.find(item=>item.id===button.dataset.id);
      if(!source)break;
      state.dialogMonth=new Date(state.budgetMonth);
      state.sourceDraft={name:source.name,initial:"0",adjustment:"",operation:"add",sourceId:source.id};
      state.overlay="edit-source";renderKeepScroll();break;
    }
    case "confirm-add-source":{
      const initial=parseAmount(state.sourceDraft.initial);
      if(!state.sourceDraft.name.trim()){showError("source-error","source_name_required");break}
      if(initial===null||!addSource(state.sourceDraft.name,state.dialogMonth,initial)){
        showError("source-error","budget_invalid_amount");break;
      }
      closeOverlay();renderKeepScroll();break;
    }
    case "confirm-edit-source":{
      const raw=state.sourceDraft.adjustment;
      const magnitude=raw.trim()===""?0:parseAmount(raw);
      if(!state.sourceDraft.name.trim()){showError("source-error","source_name_required");break}
      if(magnitude===null){showError("source-error","budget_invalid_amount");break}
      const delta=state.sourceDraft.operation==="add"?magnitude:-magnitude;
      if(!updateSource(state.sourceDraft.sourceId,state.sourceDraft.name,state.dialogMonth,delta)){
        showError("source-error","budget_invalid_amount");break;
      }
      closeOverlay();renderKeepScroll();break;
    }
    case "operation":
      state.sourceDraft.operation=button.dataset.value;renderKeepScroll();break;
    case "expense-category":
      state.expenseDraft.categoryId=button.dataset.id;renderKeepScroll();break;
    case "expense-source":
      state.expenseDraft.sourceId=button.dataset.id;renderKeepScroll();break;
    case "expense":
      state.overlay="expense";
      state.expenseDraft={amount:"",date:shortDate(state.language,state.currentDate),note:"",categoryId:null,sourceId:null};
      renderKeepScroll();break;
    case "budget-toggle":
      state.budgetExpanded=!state.budgetExpanded;renderKeepScroll();break;
    case "tab":
      state.tab=Number(button.dataset.index);renderKeepScroll();break;
    case "budget-prev":
    case "budget-next":
      state.budgetMonth.setMonth(state.budgetMonth.getMonth()+(button.dataset.action==="budget-prev"?-1:1));
      renderKeepScroll();break;
    case "toggle-setting":{
      const key=button.dataset.key;
      state.expanded.has(key)?state.expanded.delete(key):state.expanded.add(key);
      renderKeepScroll();break;
    }
    case "language":
      if(state.language!==button.dataset.value){
        await loadLanguage(button.dataset.value);
        state.language=button.dataset.value;state.page=2;state.expanded.add("language");
        renderKeepScroll();
      }
      break;
    case "theme":
      state.theme=button.dataset.value;renderKeepScroll();break;
  }
});
menuButton.addEventListener("click",()=>{state.overlay="menu";renderKeepScroll()});
document.addEventListener("keydown",event=>{
  if(event.key==="Escape"||(event.key==="Backspace"&&event.target.tagName!=="INPUT"&&event.target.tagName!=="TEXTAREA")){
    if(goBack(scroll)){event.preventDefault();renderKeepScroll()}
  }
});

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
  if(!cancelled&&distance>=64){
    state.overlay=null;renderKeepScroll();
  }else{
    sheet.style.transition="transform 180ms ease";
    sheet.style.transform="translateY(0)";
  }
}
overlay.addEventListener("pointerup",event=>finishDrag(event,false));
overlay.addEventListener("pointercancel",event=>finishDrag(event,true));

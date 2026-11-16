import {state} from "../state.js";
import {demo} from "../demo.js";
import {tr,escapeValue} from "../render-helpers.js";

export function beginCategoryCreation(parentId=null,editingId=null){
  state.categoryParentId=parentId;
  state.categoryEditingId=editingId;
  state.categoryDraft=demo.categories.find(item=>item.id===editingId)?.name??"";
  state.categoryReturnOverlay=["expense","expense-details"].includes(state.overlay)?state.overlay:null;
  state.overlay="add-category";
}
export function confirmCategoryCreation(){
  const name=state.categoryDraft.trim();
  if(!name)return false;
  const existing=demo.categories.find(item=>item.id===state.categoryEditingId);
  const created=existing??{id:`c${state.nextId++}`,name,parentId:state.categoryParentId};
  if(existing)existing.name=name;
  else demo.categories.push(created);
  if(["expense","expense-details"].includes(state.categoryReturnOverlay))state.expenseDraft.categoryId=created.id;
  state.overlay=state.categoryReturnOverlay;
  state.categoryReturnOverlay=null;
  return true;
}

export function categoryDialog(){
  const title=state.categoryEditingId?"rename_category":state.categoryParentId?"add_subcategory":"add_category_action";
  return `<div class="scrim dialog-wrap" data-action="close-overlay"><div class="overlay-stage"><div class="dialog-panel" data-stop-close role="dialog" aria-modal="true" aria-labelledby="category-dialog-title">
  <h2 id="category-dialog-title">${tr(title)}</h2>
  <input id="category-name" class="sheet-field" data-category-draft placeholder="${tr("category_name")}" aria-label="${tr("category_name")}" value="${escapeValue(state.categoryDraft)}">
  <p id="category-error" class="form-error" role="alert" hidden></p>
  <div class="dialog-actions"><button class="action-button secondary-action" data-action="close-overlay">${tr("cancel")}</button><button class="action-button primary" data-action="confirm-category">${tr(state.categoryEditingId?"save_category":"add_category")}</button></div>
  </div></div></div>`;
}

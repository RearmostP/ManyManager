import {state,closeOverlay} from "../state.js";
import {previewModel} from "../preview-model.js";
import {tr,escapeValue} from "../render-helpers.js";

export function openCategoryPicker(){
  state.pickerReturnOverlay=state.overlay;
  state.pickerParentId=null;
  state.overlay="category-picker";
}
export function chooseCategory(id){
  if(id&&previewModel.categories.some(item=>item.parentId===id)){
    state.pickerParentId=id;return;
  }
  selectCategory(id);
}
export function selectCategory(id){
  state.expenseDraft.categoryId=id;
  closeOverlay();
}
export function categoryPicker(){
  const parent=previewModel.categories.find(item=>item.id===state.pickerParentId);
  const row=(id,name,action="choose-category")=>`<button class="sheet-field category-option" data-action="${action}" ${id?`data-id="${id}"`:""} aria-pressed="${state.expenseDraft.categoryId===id}">${escapeValue(name)}</button>`;
  return `<div class="scrim dialog-wrap" data-action="close-overlay"><div class="overlay-stage"><section class="dialog-panel" data-stop-close role="dialog" aria-modal="true" aria-labelledby="picker-title">
  ${parent?`<button class="chip" data-action="category-picker-back">${tr("back")}</button>`:""}
  <h2 id="picker-title">${escapeValue(parent?.name??tr("expense_category"))}</h2>
  ${parent?row(parent.id,parent.name+" — "+tr("category_general"),"select-category"):row(null,tr("uncategorized"))}
  ${previewModel.categories.filter(item=>parent?item.parentId===parent.id:!item.parentId).map(item=>row(item.id,item.name)).join("")}
  ${parent?"":`<button class="chip new-category" data-action="add-category">${tr("new_category")}</button>`}
  <div class="dialog-actions"><button data-action="close-overlay">${tr("cancel")}</button></div>
  </section></div></div>`;
}

import {previewModel} from "../preview-model.js";
import {state} from "../state.js";
import {tr,escapeValue} from "../render-helpers.js";

export function categories(){
  const parent=previewModel.categories.find(item=>item.id===state.categoryManagementId);
  const rows=previewModel.categories.filter(item=>parent?item.parentId===parent.id:!item.parentId).map(category=>parent?
    `<div class="card category-row">${escapeValue(category.name)}</div>`:
    `<button class="card category-row category-option" data-action="open-category" data-id="${category.id}">${escapeValue(category.name)}</button>`).join("");
  return `<section class="categories-page fade-in"><button class="chip" data-action="back">${tr("back")}</button>
  <h1>${escapeValue(parent?.name??tr("expense_categories"))}</h1>
  ${rows||`<p class="category-empty">${tr(parent?"no_subcategories":"no_categories")}</p>`}
  <button class="chip secondary-action category-add" data-action="add-category">${tr(parent?"add_subcategory":"add_category_action")}</button></section>`;
}

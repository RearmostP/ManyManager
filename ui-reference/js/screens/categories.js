import {previewModel} from "../preview-model.js";
import {icon} from "../icons.js";
import {tr,escapeValue} from "../render-helpers.js";

export function categories(){
  const rows=previewModel.categories.map(category=>`<div class="card category-row" data-id="${category.id}">${escapeValue(category.name)}</div>`).join("");
  return `<section class="categories-page fade-in"><button class="icon-button" data-action="back" aria-label="${tr("back")}">${icon("right")}</button>
  <h1>${tr("expense_categories")}</h1>
  ${rows||`<p class="category-empty">${tr("no_categories")}</p>`}
  <button class="chip secondary-action category-add" data-action="add-category">${tr("add_category_action")}</button></section>`;
}

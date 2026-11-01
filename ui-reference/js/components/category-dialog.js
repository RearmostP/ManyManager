import {state} from "../state.js";
import {tr,escapeValue} from "../render-helpers.js";

export function categoryDialog(){
  return `<div class="scrim dialog-wrap" data-action="close-overlay"><div class="overlay-stage"><div class="dialog-panel" data-stop-close role="dialog" aria-modal="true" aria-labelledby="category-dialog-title">
  <h2 id="category-dialog-title">${tr("add_category_action")}</h2>
  <input id="category-name" class="sheet-field" data-category-draft placeholder="${tr("category_name")}" aria-label="${tr("category_name")}" value="${escapeValue(state.categoryDraft)}">
  <p id="category-error" class="form-error" role="alert" hidden></p>
  <div class="dialog-actions"><button data-action="close-overlay">${tr("cancel")}</button><button class="primary" data-action="confirm-category">${tr("add_category")}</button></div>
  </div></div></div>`;
}

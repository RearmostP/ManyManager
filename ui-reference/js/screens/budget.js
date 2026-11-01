import {state} from "../state.js";
import {previewModel,totalFor,amountFor} from "../preview-model.js";
import {tr,money,monthControls,escapeValue} from "../render-helpers.js";

export function budget(){
  const rows=previewModel.sources.map(source=>`<button class="card source-row" data-action="edit-source" data-id="${source.id}"><span>${escapeValue(source.name)}</span><span dir="ltr">${money(amountFor(state.budgetMonth,source.id))}</span></button>`).join("");
  return `<section class="budget-page fade-in"><h1>${tr("budget_management")}</h1>${monthControls(true)}
  <div class="card budget-card"><div class="caption">${tr("monthly_budget")}</div><div class="total">${money(totalFor(state.budgetMonth))}</div></div>
  <h2 class="source-heading">${tr("budget_by_source")}</h2>
  ${rows||`<p class="budget-no-sources">${tr("no_budget_sources_yet")}</p>`}
  <button class="chip secondary-action add-source" data-action="add-source">${tr("add_source")}</button></section>`;
}

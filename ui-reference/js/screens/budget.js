import {state} from "../state.js";
import {summaryData} from "../demo.js";
import {tr,money,monthControls,escapeValue} from "../render-helpers.js";

export function budget(){
  const snapshot=summaryData(state.budgetMonth);
  const rows=snapshot.sources.map(item=>`<button class="card source-row" data-action="edit-source" data-id="${item.source.id}"><span>${escapeValue(item.source.name)}</span><span>${tr("source_spent",money(item.spent))}<br>${item.limit==null?tr("no_source_limit"):tr("source_limit_amount",money(item.limit))+"<br>"+tr(item.remaining<0?"limit_exceeded":"limit_remaining",money(item.remaining))}</span></button>`).join("");
  return `<section class="budget-page fade-in"><h1>${tr("budget_management")}</h1>${monthControls(true)}
  <div class="card budget-card"><div class="caption">${tr("overall_budget")}</div><div class="total">${snapshot.allocated==null?tr("budget_not_set"):money(snapshot.allocated)}</div><button class="chip" data-action="overall-budget">${tr("edit_overall_budget")}</button></div>
  <h2 class="source-heading">${tr("budget_by_source")}</h2>
  ${rows||`<p class="budget-no-sources">${tr("no_budget_sources_yet")}</p>`}
  <button class="chip secondary-action add-source" data-action="add-source">${tr("add_source")}</button></section>`;
}

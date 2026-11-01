import {state} from "../state.js";
import {snapshotFor} from "../preview-model.js";
import {number} from "../i18n.js";
import {icon} from "../icons.js";
import {tr,money,periodLabel,periodDescription,escapeValue} from "../render-helpers.js";

export function home(){
  const snapshot=snapshotFor(state.currentDate);
  const total=snapshot.allocated,remaining=snapshot.remaining;
  const rows=snapshot.sources.map(item=>`<div class="budget-line budget-source"><span>${escapeValue(tr("source_remaining",item.source.name))}</span><span dir="ltr">${money(item.remaining)}</span></div>`).join("");
  return `<section class="home fade-in"><h1 class="home-title">${tr("home_title")}</h1>
  <div class="card home-card"><div class="home-month" aria-label="${escapeValue(periodDescription(state.language,state.currentDate))}">${periodLabel(state.language,state.currentDate)}</div>
  <div class="amount-display"><span class="currency">${tr("ils_symbol")}</span><span class="number">${number(state.language,snapshot.spent/100)}</span></div>
  <p class="home-total-caption">${tr("monthly_total")}</p><div class="divider"></div>
  <button class="budget-toggle ${state.budgetExpanded?"open":""}" data-action="budget-toggle" aria-expanded="${state.budgetExpanded}"><span class="spacer">${tr("remaining_budget")}</span><span class="money">${money(remaining)}</span>${icon("down","small muted")}</button>
  <div class="budget-breakdown" ${state.budgetExpanded?"":"hidden"}><div class="budget-line"><span>${tr("total_budget_for_month")}</span><span dir="ltr">${money(total)}</span></div>${rows||`<div class="budget-empty">${tr("no_budget_sources_yet")}</div>`}</div>
  </div><button class="add-expense primary" data-action="expense">${tr("add_expense")}${icon("plus","small")}</button><div class="home-bottom"></div></section>`;
}

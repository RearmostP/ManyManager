import {state} from "../state.js";
import {monthName,number} from "../i18n.js";
import {icon} from "../icons.js";
import {tr,money} from "../render-helpers.js";

export function home(){
 return `<section class="home fade-in"><h1 class="home-title">${tr("home_title")}</h1>
 <div class="card home-card"><div class="home-month">${monthName(state.language,state.currentDate)}</div>
 <div class="amount-display"><span class="currency">${tr("ils_symbol")}</span><span class="number">${number(state.language,0)}</span></div>
 <p class="home-total-caption">${tr("monthly_total")}</p><div class="divider"></div>
 <button class="budget-toggle ${state.budgetExpanded?"open":""}" data-action="budget-toggle" aria-expanded="${state.budgetExpanded}"><span class="spacer">${tr("remaining_budget")}</span><span class="money">${money()}</span>${icon("down","small muted")}</button>
 <div class="budget-breakdown" ${state.budgetExpanded?"":"hidden"}><div class="budget-line"><span>${tr("total_budget_for_month")}</span><span dir="ltr">${money()}</span></div><div class="budget-empty">${tr("no_budget_sources_yet")}</div></div>
 </div><button class="add-expense primary" data-action="expense">${tr("add_expense")}${icon("plus","small")}</button><div class="home-bottom"></div></section>`;
}

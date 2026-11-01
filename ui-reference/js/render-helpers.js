import {state} from "./state.js";
import {t,monthName,number} from "./i18n.js";
import {icon} from "./icons.js";

export const tr=(key,...args)=>t(state.language,key,...args);
export const money=(cents=0)=>tr("ils_amount",new Intl.NumberFormat(
  state.language==="he"?"he-IL":state.language==="ru"?"ru-RU":"en-US",
  {minimumFractionDigits:0,maximumFractionDigits:2}
).format(cents/100));
export const escapeValue=value=>String(value).replaceAll("&","&amp;").replaceAll('"',"&quot;").replaceAll("<","&lt;");
export const monthControls=(budget=false)=>`<div class="month-row ${budget?"":"summary-month"}">
<button class="icon-button" data-action="${budget?"budget-prev":"none"}" aria-label="${tr("previous_month")}" ${budget?"":"disabled style='opacity:.45'"}>${icon("right")}</button>
<span class="month-label">${monthName(state.language,budget?state.budgetMonth:state.currentDate)}</span>
<button class="icon-button" data-action="${budget?"budget-next":"none"}" aria-label="${tr("next_month")}" ${budget?"":"disabled style='opacity:.45'"}>${icon("left")}</button></div>`;

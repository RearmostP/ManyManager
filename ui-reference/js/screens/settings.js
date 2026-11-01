import {state} from "../state.js";
import {icon} from "../icons.js";
import {tr} from "../render-helpers.js";

function expandable(key,iconName,title,subtitle,body){
  const open=state.expanded.has(key);
  return `<button class="setting-row" data-action="toggle-setting" data-key="${key}" aria-expanded="${open}">${icon(iconName)}<span class="setting-copy"><span class="setting-title">${tr(title)}</span><span class="setting-subtitle">${subtitle}</span></span><span class="setting-chevron">‹</span></button><div class="setting-details" ${open?"":"hidden"}>${body}</div>`;
}
export function settings(){
  const languageNames={he:"language_he",en:"language_en",ru:"language_ru"};
  const langChips=["he","en","ru"].map(code=>`<button class="chip ${code===state.language?"selected":""}" data-action="language" data-value="${code}">${code===state.language?tr("language_selected",tr(languageNames[code])):tr(languageNames[code])}</button>`).join("");
  const themeChips=["light","dark"].map(code=>`<button class="chip ${code===state.theme?"selected":""}" data-action="theme" data-value="${code}">${tr(code==="light"?"theme_light":"theme_dark")}</button>`).join("");
  return `<section class="settings fade-in"><p class="intro">${tr("settings_intro")}</p><h1>${tr("settings")}</h1>
  <p class="settings-heading">${tr("language_and_appearance")}</p>
  ${expandable("language","language","app_language",tr(languageNames[state.language]),`<div class="chips">${langChips}</div>`)}
  ${expandable("theme","theme","app_appearance",tr(state.theme==="light"?"theme_light":"theme_dark"),`<div class="chips">${themeChips}</div>`)}
  </section>`;
}

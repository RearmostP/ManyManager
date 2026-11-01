const dictionaries={};
export async function loadLanguage(language){
  if(!dictionaries[language]){
    const response=await fetch(`locales/${language}.json`);
    if(!response.ok)throw new Error(`Could not load ${language} translations`);
    dictionaries[language]=await response.json();
  }
}
export function t(language,key,...values){
  let text=dictionaries[language]?.[key]??key;
  values.forEach(value=>text=text.replace(/%\d+\$s/,String(value)));
  return text;
}
export function monthName(language,date){
  return new Intl.DateTimeFormat(language==="he"?"he-IL":language==="ru"?"ru-RU":"en-US",{month:"long",year:"numeric"}).format(date);
}
export function shortDate(language,date){
  return new Intl.DateTimeFormat(language==="he"?"he-IL":language==="ru"?"ru-RU":"en-US",{day:"numeric",month:"numeric",year:"numeric"}).format(date);
}
export function number(language,value){
  return new Intl.NumberFormat(language==="he"?"he-IL":language==="ru"?"ru-RU":"en-US").format(value);
}

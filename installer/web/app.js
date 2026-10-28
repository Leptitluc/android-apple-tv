"use strict";

const T = {
  fr: {
    welcomeTitle: "Installer Accueil TV",
    welcomeLead: "Un accueil rapide et sans pub pour ta télé Android, façon Apple TV. L'installation prend environ 5 minutes.",
    welcome1: "Rien n'est désinstallé : tout est réversible.",
    welcome2: "Une sauvegarde de la télé est faite avant toute modification.",
    welcome3: "Les outils nécessaires sont téléchargés automatiquement.",
    start: "Commencer",
    restoreLink: "Tout annuler sur une télé déjà installée",
    step1: "Étape 1 sur 3",
    step2: "Étape 2 sur 3",
    step3: "Étape 3 sur 3",
    prepTitle: "Prépare ta télé",
    prepLead: "À faire une seule fois, avec la télécommande.",
    prep1Title: "Même réseau Wi-Fi",
    prep1: "Connecte la télé au même réseau que cet ordinateur (Wi-Fi ou câble de la même box).",
    prep2Title: "Active le mode développeur",
    prep2: "Paramètres → Système → À propos. Descends sur « Version d'Android TV OS » (ou « Numéro de build ») et appuie 7 fois dessus, jusqu'au message « Vous êtes développeur ».",
    prep3Title: "Active le débogage USB",
    prep3: "Paramètres → Système → Options pour les développeurs → active « Débogage USB ». Il sert aussi pour le Wi-Fi.",
    prepDone: "C'est fait sur ma télé",
    back: "Retour",
    continue: "Continuer",
    ipTitle: "Trouve ta télé",
    ipLead: "Choisis ta télé si elle apparaît ci-dessous, sinon tape son adresse IP.",
    scanning: "Recherche des télés sur ton réseau…",
    scanFound: "Télés trouvées sur ton réseau :",
    scanNone: "Aucune télé trouvée automatiquement : tape son adresse ci-dessous.",
    ipLabel: "Adresse IP de la télé",
    ipHint: "Sur la télé : Paramètres → Réseau et Internet → ton réseau → Adresse IP (par exemple 192.168.1.20).",
    connect: "Se connecter",
    retry: "Réessayer",
    connecting: "Connexion à la télé…",
    connectingText: "Ça peut prendre quelques secondes.",
    lookTv: "Regarde ta télé",
    tvDialogTitle: "Autoriser le débogage USB ?",
    tvDialogText: "☑ Toujours autoriser sur cet ordinateur",
    tvDialogAllow: "Autoriser",
    cantConnect: "Connexion impossible",
    connectedTo: "Connecté à {model}",
    optLead: "Voici ce qui va se passer sur la télé :",
    optUpdate: "Accueil TV {v} est déjà installé : il sera mis à jour. Ta sauvegarde existante est conservée.",
    sum1: "Sauvegarde des réglages de la télé.",
    sum2: "Désactivation (réversible) des pubs, de la télémétrie et des applis de démonstration.",
    sum3: "Installation d'Accueil TV, du bouton réglages et de l'économiseur d'écran.",
    sum4: "Accueil TV devient l'écran d'accueil.",
    optAppsTitle: "Désactiver aussi ces applis ?",
    optAppsHint: "Elles restent installées et pourront être réactivées. Laisse décoché celles que tu utilises.",
    install: "Installer",
    runInstall: "Installation en cours",
    runRestore: "Restauration en cours",
    runLead: "Laisse la télé allumée et ne touche pas à la télécommande.",
    s_tools: "Outils Android",
    s_backup: "Sauvegarde",
    s_slim: "Allègement",
    s_download: "Téléchargement",
    s_install: "Installation",
    s_keys: "Boutons de la télécommande",
    s_home: "Accueil par défaut",
    s_apps: "Applis",
    s_settings: "Réglages",
    s_uninstall: "Désinstallation",
    doneTitle: "Accueil TV est installé",
    doneText: "Prends ta télécommande et appuie sur le bouton Accueil.",
    doneKept: "Accueil TV est installé, mais la télé a gardé son accueil d'origine.",
    doneHint: "Sauvegarde : {path}. Pour tout annuler, relance cet installateur et choisis « Tout annuler ».",
    restTitle: "Tout annuler sur {model} ?",
    restLead: "La télé retrouve l'état de la sauvegarde faite à l'installation :",
    rest1: "Les applis désactivées sont réactivées.",
    rest2: "Les réglages d'origine sont rétablis (accueil, économiseur, animations).",
    rest3: "Accueil TV est désinstallé.",
    restoreNow: "Tout annuler",
    restoredTitle: "La télé est comme avant",
    restoredText: "L'accueil d'origine est revenu.",
    cancel: "Annuler",
    quit: "Terminer",
    closed: "Tu peux fermer cet onglet.",
    yes: "Oui",
    no: "Non",
    busy: "Une opération est déjà en cours.",
  },
  en: {
    welcomeTitle: "Install Accueil TV",
    welcomeLead: "A fast, ad-free home screen for your Android TV, Apple TV style. Setup takes about 5 minutes.",
    welcome1: "Nothing is uninstalled: everything can be undone.",
    welcome2: "A backup of the TV is made before any change.",
    welcome3: "The tools it needs are downloaded automatically.",
    start: "Get started",
    restoreLink: "Undo everything on a TV that already has it",
    step1: "Step 1 of 3",
    step2: "Step 2 of 3",
    step3: "Step 3 of 3",
    prepTitle: "Prepare your TV",
    prepLead: "Just once, with the remote.",
    prep1Title: "Same Wi-Fi network",
    prep1: "Connect the TV to the same network as this computer (Wi-Fi or a cable on the same router).",
    prep2Title: "Turn on developer mode",
    prep2: "Settings → System → About. Scroll to \"Android TV OS build\" (or \"Build\") and press it 7 times, until you see \"You are now a developer!\".",
    prep3Title: "Turn on USB debugging",
    prep3: "Settings → System → Developer options → turn on \"USB debugging\". It is also used over Wi-Fi.",
    prepDone: "Done on my TV",
    back: "Back",
    continue: "Continue",
    ipTitle: "Find your TV",
    ipLead: "Pick your TV if it appears below, otherwise type its IP address.",
    scanning: "Looking for TVs on your network…",
    scanFound: "TVs found on your network:",
    scanNone: "No TV found automatically: type its address below.",
    ipLabel: "TV IP address",
    ipHint: "On the TV: Settings → Network & Internet → your network → IP address (e.g. 192.168.1.20).",
    connect: "Connect",
    retry: "Try again",
    connecting: "Connecting to the TV…",
    connectingText: "This can take a few seconds.",
    lookTv: "Look at your TV",
    tvDialogTitle: "Allow USB debugging?",
    tvDialogText: "☑ Always allow from this computer",
    tvDialogAllow: "Allow",
    cantConnect: "Can't connect",
    connectedTo: "Connected to {model}",
    optLead: "Here is what will happen on the TV:",
    optUpdate: "Accueil TV {v} is already installed: it will be updated. Your existing backup is kept.",
    sum1: "Backup of the TV's settings.",
    sum2: "Ads, telemetry and demo apps turned off (reversible).",
    sum3: "Accueil TV, the settings button and the screensaver installed.",
    sum4: "Accueil TV becomes the home screen.",
    optAppsTitle: "Also turn off these apps?",
    optAppsHint: "They stay installed and can be turned back on. Leave unticked the ones you use.",
    install: "Install",
    runInstall: "Installing",
    runRestore: "Restoring",
    runLead: "Keep the TV on and don't touch the remote.",
    s_tools: "Android tools",
    s_backup: "Backup",
    s_slim: "Slimming down",
    s_download: "Download",
    s_install: "Install",
    s_keys: "Remote buttons",
    s_home: "Default home screen",
    s_apps: "Apps",
    s_settings: "Settings",
    s_uninstall: "Uninstall",
    doneTitle: "Accueil TV is installed",
    doneText: "Pick up your remote and press the Home button.",
    doneKept: "Accueil TV is installed, but the TV kept its original home screen.",
    doneHint: "Backup: {path}. To undo everything, run this installer again and choose \"Undo everything\".",
    restTitle: "Undo everything on {model}?",
    restLead: "The TV goes back to the backup made during the install:",
    rest1: "Turned-off apps are turned back on.",
    rest2: "Original settings come back (home screen, screensaver, animations).",
    rest3: "Accueil TV is uninstalled.",
    restoreNow: "Undo everything",
    restoredTitle: "The TV is back to how it was",
    restoredText: "The original home screen is back.",
    cancel: "Cancel",
    quit: "Finish",
    closed: "You can close this tab.",
    yes: "Yes",
    no: "No",
    busy: "Something is already running.",
  },
};

const STEPS = {
  install: ["backup", "slim", "download", "install", "keys", "home"],
  restore: ["apps", "settings", "uninstall"],
};

const token = new URLSearchParams(location.search).get("t");
const $ = (id) => document.getElementById(id);
let lang = navigator.language && navigator.language.toLowerCase().startsWith("fr") ? "fr" : "en";
let mode = "install";
let device = null;
let source = null;

function t(key, vars) {
  let s = T[lang][key] || key;
  for (const k in vars || {}) s = s.replace("{" + k + "}", vars[k]);
  return s;
}

function applyLang() {
  document.documentElement.lang = lang;
  document.querySelectorAll("[data-t]").forEach((el) => (el.textContent = t(el.dataset.t)));
  document.querySelectorAll(".lang button").forEach((b) => b.classList.toggle("on", b.dataset.lang === lang));
  if (device) fillDevice();
}

function show(id) {
  document.querySelectorAll(".screen").forEach((s) => s.classList.toggle("on", s.id === id));
  window.scrollTo(0, 0);
  const first = $(id).querySelector("input:not([type=checkbox]), button.primary:not(:disabled)");
  if (first && id !== "s-run") setTimeout(() => first.focus(), 50);
}

async function call(path, body) {
  const res = await fetch("/api/" + path, {
    method: body ? "POST" : "GET",
    headers: { "X-Token": token, "Content-Type": "application/json" },
    body: body ? JSON.stringify(Object.assign({ lang }, body)) : undefined,
  });
  return res.json();
}

// Suit une tâche du serveur (connexion, installation, restauration).
function follow(onEvent) {
  if (source) source.close();
  source = new EventSource("/api/events?t=" + token + "&from=0");
  source.onmessage = (m) => {
    const e = JSON.parse(m.data);
    if (e.type === "question") return ask(e.text);
    onEvent(e);
    if (e.type === "done") source.close();
  };
}

function ask(text) {
  $("modal-text").textContent = text;
  $("modal").hidden = false;
  $("modal-yes").focus();
}
function reply(yes) {
  $("modal").hidden = true;
  call("answer", { yes });
}

// Écrans

function start(m) {
  mode = m;
  $("prep-done").checked = false;
  $("prep-next").disabled = true;
  show("s-prep");
}

async function scan() {
  $("scan-spin").hidden = false;
  $("scan-text").textContent = t("scanning");
  $("found-list").innerHTML = "";
  const res = await call("scan").catch(() => ({ devices: [] }));
  $("scan-spin").hidden = true;
  const list = res.devices || [];
  $("scan-text").textContent = list.length ? t("scanFound") : t("scanNone");
  list.forEach((ip) => {
    const b = document.createElement("button");
    b.textContent = ip;
    b.onclick = () => {
      $("ip").value = ip;
      checkIp();
      document.querySelectorAll("#found-list button").forEach((x) => x.classList.toggle("on", x === b));
    };
    $("found-list").appendChild(b);
  });
  if (list.length === 1 && !$("ip").value) list.length && $("found-list").firstChild.click();
}

function checkIp() {
  $("ip-next").disabled = !/^\d{1,3}(\.\d{1,3}){3}(:\d+)?$/.test($("ip").value.trim());
}

async function connect() {
  show("s-connect");
  $("tv-illus").hidden = true;
  $("connect-spin").hidden = false;
  $("connect-actions").hidden = true;
  $("connect-title").textContent = t("connecting");
  $("connect-text").textContent = t("connectingText");
  const r = await call("connect", { ip: $("ip").value.trim() });
  if (!r.ok) return connectFail(t("busy"));
  follow((e) => {
    if (e.type === "step" && e.status === "wait") {
      $("tv-illus").hidden = false;
      $("connect-title").textContent = t("lookTv");
      $("connect-text").textContent = e.text;
    } else if (e.type === "step" && e.status === "run") {
      $("connect-text").textContent = e.text;
    } else if (e.type === "done") {
      if (!e.ok) return connectFail(e.text);
      device = e.data;
      fillDevice();
      show(mode === "install" ? "s-options" : "s-restore");
    }
  });
}

function connectFail(text) {
  $("tv-illus").hidden = true;
  $("connect-spin").hidden = true;
  $("connect-title").textContent = t("cantConnect");
  $("connect-text").textContent = text;
  $("connect-actions").hidden = false;
}

function fillDevice() {
  $("opt-title").textContent = t("connectedTo", { model: device.model });
  $("opt-lead").textContent = device.current ? t("optUpdate", { v: device.current }) : t("optLead");
  $("rest-title").textContent = t("restTitle", { model: device.model });
  const list = $("opt-list");
  const checked = new Set([...list.querySelectorAll("input:checked")].map((i) => i.value));
  list.innerHTML = "";
  (device.optional || []).forEach((o) => {
    const l = document.createElement("label");
    l.innerHTML = '<input type="checkbox"><span></span>';
    l.querySelector("input").value = o.pkg;
    l.querySelector("input").checked = checked.has(o.pkg);
    l.querySelector("span").textContent = o.label;
    list.appendChild(l);
  });
  $("opt-apps").hidden = !(device.optional || []).length;
}

function run(kind, body) {
  show("s-run");
  $("run-title").textContent = t(kind === "install" ? "runInstall" : "runRestore");
  $("run-actions").hidden = true;
  const ul = $("steps");
  ul.innerHTML = "";
  const old = ul.parentNode.querySelector(".error");
  if (old) old.remove();
  STEPS[kind].forEach((id) => {
    const li = document.createElement("li");
    li.id = "st-" + id;
    li.className = "pending";
    li.innerHTML = '<span class="ico"><span class="dot"></span></span><span class="txt"><span class="name"></span><span class="sub"></span></span>';
    li.querySelector(".name").textContent = t("s_" + id);
    ul.appendChild(li);
  });
  call(kind, body).then((r) => {
    if (!r.ok) return runFail(t("busy"));
    follow((e) => {
      if (e.type === "step") setStep(e);
      if (e.type === "done") (e.ok ? done(kind, e.data) : runFail(e.text));
    });
  });
}

function setStep(e) {
  let li = $("st-" + e.step);
  if (!li) return;
  li.className = e.status;
  const ico = li.querySelector(".ico");
  ico.className = "ico " + e.status;
  ico.innerHTML = e.status === "run" ? '<span class="spinner"></span>'
    : e.status === "ok" ? "<b>✓</b>" : e.status === "warn" ? "<b>!</b>" : e.status === "fail" ? "<b>✕</b>" : '<span class="dot"></span>';
  li.querySelector(".sub").textContent = e.text || "";
}

function runFail(text) {
  const p = document.createElement("p");
  p.className = "error";
  p.textContent = text;
  $("steps").after(p);
  $("run-actions").hidden = false;
}

function done(kind, data) {
  show("s-done");
  if (kind === "install") {
    $("done-title").textContent = t("doneTitle");
    $("done-text").textContent = data.home ? t("doneText") : t("doneKept");
    $("done-hint").textContent = t("doneHint", { path: data.backup });
    if (data.shot) {
      $("done-shot").src = "/api/shot?t=" + token + "&n=" + Date.now();
      $("done-shot").hidden = false;
    }
  } else {
    $("done-title").textContent = t("restoredTitle");
    $("done-text").textContent = t("restoredText");
    $("done-hint").textContent = "";
    $("done-shot").hidden = true;
  }
}

// Branchements

document.querySelectorAll(".lang button").forEach((b) => (b.onclick = () => { lang = b.dataset.lang; applyLang(); }));
document.querySelectorAll("[data-back]").forEach((b) => (b.onclick = () => show(b.dataset.back)));
$("go-install").onclick = () => start("install");
$("go-restore").onclick = (e) => { e.preventDefault(); start("restore"); };
$("prep-done").onchange = () => ($("prep-next").disabled = !$("prep-done").checked);
$("prep-next").onclick = () => { show("s-ip"); checkIp(); scan(); };
$("ip").oninput = checkIp;
$("ip").onkeydown = (e) => { if (e.key === "Enter" && !$("ip-next").disabled) connect(); };
$("ip-next").onclick = connect;
$("connect-retry").onclick = connect;
$("do-install").onclick = () => run("install", { disable: [...$("opt-list").querySelectorAll("input:checked")].map((i) => i.value) });
$("do-restore").onclick = () => run("restore", {});
$("run-retry").onclick = () => run(mode, mode === "install" ? { disable: [...$("opt-list").querySelectorAll("input:checked")].map((i) => i.value) } : {});
$("modal-yes").onclick = () => reply(true);
$("modal-no").onclick = () => reply(false);
$("quit").onclick = async () => {
  await call("quit").catch(() => {});
  document.body.innerHTML = '<p style="text-align:center;margin-top:30vh;color:var(--muted)">' + t("closed") + "</p>";
  window.close();
};

setInterval(() => call("ping").catch(() => {}), 5000);
applyLang();
show("s-welcome");

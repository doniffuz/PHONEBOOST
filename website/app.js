(function () {
  "use strict";
  var C = window.PB_CONFIG || {}, D = window.PB_I18N;
  var lang = "uz", amount = 0;
  var AMOUNTS = [10000, 20000, 50000, 100000];
  try { var s = localStorage.getItem("pb_lang"); if (D[s]) lang = s; else { var n = (navigator.language || "uz").slice(0, 2); if (D[n]) lang = n; } } catch (e) {}
  function t(k) { return D[lang][k] || ""; }
  function el(tag, text, cls) { var e = document.createElement(tag); if (text != null) e.textContent = text; if (cls) e.className = cls; return e; }
  function isHttps(u) { try { return new URL(u).protocol === "https:"; } catch (e) { return false; } }
  function fmt(n) { return new Intl.NumberFormat(lang === "uz" ? "ru-RU" : lang).format(n); }
  function doc(id, items) { var b = document.getElementById(id); b.textContent = ""; items.forEach(function (i) { b.appendChild(el("h3", i[0])); b.appendChild(el("p", i[1], "muted")); }); }

  function renderAmounts() {
    var box = document.getElementById("amounts"); box.textContent = "";
    AMOUNTS.forEach(function (a) {
      var b = el("button", fmt(a) + " " + t("som")); b.type = "button"; b.setAttribute("aria-pressed", String(amount === a));
      b.onclick = function () { amount = a; document.getElementById("custom").value = ""; renderAmounts(); }; box.appendChild(b);
    });
  }
  function render() {
    document.documentElement.lang = lang;
    document.querySelectorAll("[data-i18n]").forEach(function (n) { n.textContent = t(n.getAttribute("data-i18n")); });
    document.querySelectorAll(".lang button").forEach(function (b) { b.setAttribute("aria-pressed", String(b.dataset.lang === lang)); });
    var g = document.getElementById("featureGrid"); g.textContent = "";
    D[lang].features.forEach(function (f) { var c = el("div", null, "card"); c.appendChild(el("h3", f[0])); c.appendChild(el("p", f[1], "muted")); g.appendChild(c); });
    var sh = document.getElementById("shots"); sh.textContent = "";
    for (var i = 1; i <= 4; i++) sh.appendChild(el("div", t("shot") + " " + i, "shot"));
    var dl = document.getElementById("dlBtn"), note = document.getElementById("dlNote");
    dl.textContent = t("dl_btn");
    if (isHttps(C.DOWNLOAD_URL)) { dl.href = C.DOWNLOAD_URL; dl.removeAttribute("aria-disabled"); note.textContent = ""; }
    else { dl.removeAttribute("href"); dl.setAttribute("aria-disabled", "true"); note.textContent = t("dl_none"); }
    renderAmounts();
    var on = isHttps(C.DONATION_API), btn = document.getElementById("donBtn");
    btn.textContent = on ? t("don_btn") : t("don_unavail"); btn.disabled = !on;
    var st = document.getElementById("donStatus"); if (!on) { st.className = "status warn"; st.textContent = t("don_status_off"); } else if (!st.dataset.busy) { st.textContent = ""; st.className = "status"; }
    doc("privacyBody", D[lang].privacy); doc("termsBody", D[lang].terms);
    renderTop();
  }

  var supporters = null, topErr = false;
  function renderTop() {
    var ol = document.getElementById("topList"), st = document.getElementById("topStatus"); ol.textContent = "";
    if (topErr) { st.textContent = t("top_err"); return; }
    if (supporters === null) { st.textContent = t("top_loading"); return; }
    if (!supporters.length) { st.textContent = t("top_empty"); return; }
    st.textContent = "";
    supporters.forEach(function (s, i) {
      var li = el("li"); li.appendChild(el("span", (i + 1) + ". " + (s.name || t("anon")))); li.appendChild(el("strong", fmt(s.amount) + " " + t("som"))); ol.appendChild(li);
    });
  }
  function loadTop() {
    fetch(C.SUPPORTERS_URL || "data/supporters.json", { cache: "no-store" }).then(function (r) { if (!r.ok) throw 0; return r.json(); }).then(function (arr) {
      if (!Array.isArray(arr)) throw 0;
      supporters = arr.filter(function (x) { return x && Number.isFinite(x.amount) && x.amount > 0; }).map(function (x) {
        var nm = (!x.anonymous && typeof x.name === "string") ? x.name.trim().slice(0, 40) : "";
        return { name: nm || null, amount: Math.round(x.amount) };
      }).sort(function (a, b) { return b.amount - a.amount; }).slice(0, 10);
      renderTop();
    }).catch(function () { topErr = true; renderTop(); });
  }

  document.querySelectorAll(".lang button").forEach(function (b) { b.onclick = function () { lang = b.dataset.lang; try { localStorage.setItem("pb_lang", lang); } catch (e) {} render(); }; });
  document.getElementById("custom").addEventListener("input", function (e) { amount = parseInt(e.target.value, 10) || 0; renderAmounts(); });
  document.getElementById("donBtn").addEventListener("click", function () {
    var st = document.getElementById("donStatus");
    if (!isHttps(C.DONATION_API)) return;
    if (!(amount >= 1000 && amount <= 10000000)) { st.className = "status warn"; st.textContent = t("don_bad"); return; }
    var anon = document.getElementById("anon").checked, nm = document.getElementById("dname").value.trim().slice(0, 40);
    st.dataset.busy = "1"; st.className = "status"; st.textContent = t("don_wait");
    fetch(C.DONATION_API.replace(/\/$/, "") + "/donations", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ amount: amount, anonymous: anon, displayName: anon ? null : nm || null }) })
      .then(function (r) { if (!r.ok) throw 0; return r.json(); })
      .then(function (j) { if (!isHttps(j && j.checkoutUrl)) throw 0; window.location.assign(j.checkoutUrl); })
      .catch(function () { delete st.dataset.busy; st.className = "status warn"; st.textContent = t("don_err"); });
  });
  render(); loadTop();
})();

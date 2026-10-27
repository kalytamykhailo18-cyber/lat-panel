// Panel interactions. Plain JavaScript, no libraries.
(function () {
  'use strict';

  var ICON_OK = '<svg class="icon" viewBox="0 0 24 24"><path d="M20 6 9 17l-5-5"/></svg>';
  var ICON_ERR = '<svg class="icon" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 8v5M12 16h.01"/></svg>';

  function $(sel, root) { return (root || document).querySelector(sel); }
  function $$(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }
  function esc(s) { return String(s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }

  // ---------- Toasts ----------
  function toastHost() {
    var host = $('.toasts');
    if (!host) { host = document.createElement('div'); host.className = 'toasts'; document.body.appendChild(host); }
    return host;
  }
  function hideToast(el) {
    el.classList.add('hide');
    setTimeout(function () { el.remove(); }, 300);
  }
  function wireToast(el) {
    var close = $('.toast-close', el);
    if (close) close.addEventListener('click', function () { hideToast(el); });
    // Success hides by itself; errors stay until the user closes them.
    if (el.classList.contains('toast-ok')) setTimeout(function () { hideToast(el); }, 5000);
  }
  window.panelToast = function (type, text) {
    var el = document.createElement('div');
    el.className = 'toast toast-' + type;
    el.innerHTML = (type === 'ok' ? ICON_OK : ICON_ERR) + '<div class="toast-body">' + esc(text) + '</div><button class="toast-close" aria-label="Cerrar">×</button>';
    toastHost().appendChild(el);
    wireToast(el);
  };
  $$('.toast').forEach(wireToast);

  // ---------- Confirmation dialog (replaces the browser's confirm()) ----------
  function confirmDialog(opts) {
    return new Promise(function (resolve) {
      var back = document.createElement('div');
      back.className = 'dialog-backdrop';
      back.innerHTML =
        '<div class="dialog" role="dialog" aria-modal="true">' +
        '<h3>' + esc(opts.title || '¿Confirmar?') + '</h3>' +
        (opts.text ? '<p>' + esc(opts.text) + '</p>' : '') +
        '<div class="actions"><button class="btn btn-ghost" data-act="no">Cancelar</button>' +
        '<button class="btn ' + (opts.danger ? 'btn-confirm-danger' : 'btn-primary') + '" data-act="yes">' + esc(opts.ok || 'Confirmar') + '</button></div></div>';
      document.body.appendChild(back);
      var done = function (v) { back.remove(); document.removeEventListener('keydown', onKey); resolve(v); };
      var onKey = function (e) { if (e.key === 'Escape') done(false); if (e.key === 'Enter') done(true); };
      document.addEventListener('keydown', onKey);
      back.addEventListener('click', function (e) {
        if (e.target === back) return done(false);
        var act = e.target.closest('[data-act]');
        if (act) done(act.getAttribute('data-act') === 'yes');
      });
      $('[data-act="yes"]', back).focus();
    });
  }

  // ---------- Forms: confirmation + loading state on the submit button ----------
  function setLoading(btn) {
    if (!btn || btn.classList.contains('loading')) return;
    if (!$('.spinner', btn)) btn.insertAdjacentHTML('afterbegin', '<span class="spinner"></span>');
    var label = btn.getAttribute('data-loading');
    if (label) {
      var textNode = Array.prototype.slice.call(btn.childNodes).filter(function (n) { return n.nodeType === 3 && n.textContent.trim(); }).pop();
      if (textNode) textNode.textContent = ' ' + label;
    }
    btn.classList.add('loading');
  }
  $$('form').forEach(function (form) {
    form.addEventListener('submit', function (e) {
      var btn = e.submitter || $('button[type=submit]', form);
      if (form.hasAttribute('data-confirm') && !form._confirmed) {
        e.preventDefault();
        confirmDialog({
          title: form.getAttribute('data-confirm'),
          text: form.getAttribute('data-confirm-text'),
          ok: form.getAttribute('data-confirm-ok'),
          danger: form.hasAttribute('data-danger'),
        }).then(function (yes) {
          if (!yes) return;
          form._confirmed = true;
          setLoading(btn);
          if (form.requestSubmit && btn) form.requestSubmit(btn); else form.submit();
        });
        return;
      }
      setLoading(btn);   // also prevents a double submit
    });
  });

  // ---------- Copy buttons ----------
  document.addEventListener('click', function (e) {
    var btn = e.target.closest('[data-copy]');
    if (!btn) return;
    e.preventDefault(); e.stopPropagation();
    var text = btn.getAttribute('data-copy');
    var ok = function () { window.panelToast('ok', 'Copiado: ' + text); };
    if (navigator.clipboard && window.isSecureContext) navigator.clipboard.writeText(text).then(ok);
    else { var t = document.createElement('textarea'); t.value = text; document.body.appendChild(t); t.select(); document.execCommand('copy'); t.remove(); ok(); }
  });

  // ---------- Clickable table rows ----------
  document.addEventListener('click', function (e) {
    var row = e.target.closest('tr[data-href]');
    if (!row || e.target.closest('a, button, form, input, select, textarea')) return;
    window.location = row.getAttribute('data-href');
  });

  // ---------- Relative times ("hace 5 min"), exact date on hover ----------
  function relative(iso) {
    var s = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
    if (isNaN(s)) return '';
    var future = s < 0; s = Math.abs(s);
    var v, u;
    if (s < 45) return future ? 'en unos segundos' : 'hace unos segundos';
    if (s < 3600) { v = Math.round(s / 60); u = v === 1 ? 'minuto' : 'min'; }
    else if (s < 86400) { v = Math.round(s / 3600); u = v === 1 ? 'hora' : 'horas'; }
    else if (s < 86400 * 30) { v = Math.round(s / 86400); u = v === 1 ? 'día' : 'días'; }
    else if (s < 86400 * 365) { v = Math.round(s / (86400 * 30)); u = v === 1 ? 'mes' : 'meses'; }
    else { v = Math.round(s / (86400 * 365)); u = v === 1 ? 'año' : 'años'; }
    return (future ? 'en ' : 'hace ') + v + ' ' + u;
  }
  function updateTimes(root) {
    $$('time[data-rel]', root).forEach(function (t) { t.textContent = relative(t.getAttribute('datetime')); });
  }
  updateTimes();
  setInterval(updateTimes, 30000);

  // ---------- Mobile menu ----------
  var menuBtn = $('.menu-btn');
  if (menuBtn) menuBtn.addEventListener('click', function () {
    var nav = $('.topbar nav');
    nav.classList.toggle('open');
    menuBtn.setAttribute('aria-expanded', nav.classList.contains('open'));
  });

  // ---------- Show / hide password ----------
  $$('.pw-toggle').forEach(function (b) {
    b.addEventListener('click', function () {
      var input = $('input', b.parentNode);
      var show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      b.textContent = show ? 'Ocultar' : 'Mostrar';
    });
  });

  // ---------- Character counters ----------
  $$('[data-counter]').forEach(function (input) {
    var limit = parseInt(input.getAttribute('data-counter'), 10);
    var label = $('label[for="' + input.id + '"]');
    if (!label) return;
    var c = document.createElement('span'); c.className = 'counter'; label.appendChild(c);
    var upd = function () { c.textContent = input.value.length + ' / ' + limit; c.classList.toggle('over', input.value.length > limit); };
    input.addEventListener('input', upd); upd();
  });

  // ---------- Expiry presets ----------
  $$('[data-preset-for]').forEach(function (box) {
    var input = document.getElementById(box.getAttribute('data-preset-for'));
    var chips = $$('.chip', box);
    var mark = function (chip) { chips.forEach(function (c) { c.classList.toggle('on', c === chip); }); };
    chips.forEach(function (chip) {
      chip.addEventListener('click', function () {
        var months = parseInt(chip.getAttribute('data-months'), 10);
        if (isNaN(months)) { input.value = ''; }
        else {
          var d = new Date(); d.setMonth(d.getMonth() + months);
          input.value = d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
        }
        mark(chip);
        input.dispatchEvent(new Event('change'));
      });
    });
    input.addEventListener('input', function () { mark(null); });
  });

  // ---------- Required fields only when activating ----------
  var statusSel = $('#status[data-required-when]');
  if (statusSel) {
    var reqWhen = statusSel.getAttribute('data-required-when');
    var updReq = function () {
      var need = statusSel.value === reqWhen;
      $$('[data-required-if-active]').forEach(function (f) {
        f.required = need;
        var lbl = $('label[for="' + f.id + '"] .req'); if (lbl) lbl.style.visibility = need ? 'visible' : 'hidden';
      });
    };
    statusSel.addEventListener('change', updReq); updReq();
  }

  // ---------- Auto-refreshing fragments (dashboard waiting list) ----------
  $$('[data-poll]').forEach(function (box) {
    var url = box.getAttribute('data-poll');
    var every = parseInt(box.getAttribute('data-poll-every') || '10', 10) * 1000;
    var note = $(box.getAttribute('data-poll-note'));
    var last = Date.now();
    var known = {};
    $$('[data-id]', box).forEach(function (r) { known[r.getAttribute('data-id')] = 1; });
    var tick = function () {
      fetch(url, { credentials: 'same-origin', headers: { 'X-Requested-With': 'fetch' } })
        .then(function (r) { if (!r.ok) throw new Error(r.status); return r.text(); })
        .then(function (html) {
          box.innerHTML = html;
          $$('[data-id]', box).forEach(function (r) {
            var id = r.getAttribute('data-id');
            if (!known[id]) { r.classList.add('is-new'); known[id] = 1; }
          });
          updateTimes(box);
          last = Date.now();
        })
        .catch(function () { /* keep the last content; the note shows how old it is */ });
    };
    setInterval(tick, every);
    if (note) setInterval(function () {
      var s = Math.round((Date.now() - last) / 1000);
      note.textContent = 'Se actualiza solo · ' + (s < 5 ? 'recién actualizado' : 'hace ' + s + ' s');
    }, 1000);
  });

  // ---------- Live device status after activating ----------
  var live = $('[data-live-status]');
  if (live) {
    var liveUrl = live.getAttribute('data-live-status');
    var poll = function () {
      fetch(liveUrl, { credentials: 'same-origin' }).then(function (r) { return r.json(); }).then(function (d) {
        if (d.connected_after_activation) {
          live.className = 'live-status ok';
          live.innerHTML = '<span class="check">' + ICON_OK.replace('class="icon"', 'class="icon icon-sm"') + '</span><div><strong>El equipo ya ingresó</strong><small>Se conectó ' + esc(relative(d.last_seen_iso)) + '. El cliente ya puede ver el contenido.</small></div>';
          clearInterval(timer);
        }
      }).catch(function () {});
    };
    var timer = setInterval(poll, 3000);
    poll();
  }

  // ---------- Message recipients preview ----------
  var recip = $('[data-recipients]');
  if (recip) {
    var devInput = document.getElementById(recip.getAttribute('data-recipients-input'));
    var t;
    var upd2 = function () {
      clearTimeout(t);
      t = setTimeout(function () {
        fetch(recip.getAttribute('data-recipients') + '&device=' + encodeURIComponent(devInput.value.trim()), { credentials: 'same-origin' })
          .then(function (r) { return r.json(); })
          .then(function (d) {
            recip.className = 'hint' + (d.ok ? '' : ' warn');
            recip.textContent = d.text;
            recip.style.color = d.ok ? '' : 'var(--warn)';
          }).catch(function () {});
      }, 300);
    };
    devInput.addEventListener('input', upd2); upd2();
  }

  // ---------- DNS connection test ----------
  document.addEventListener('click', function (e) {
    var btn = e.target.closest('[data-dns-test]');
    if (!btn) return;
    e.preventDefault();
    var out = document.getElementById(btn.getAttribute('data-out'));
    setLoading(btn);
    out.textContent = 'Probando…'; out.style.color = 'var(--muted)';
    fetch(btn.getAttribute('data-dns-test'), { credentials: 'same-origin' })
      .then(function (r) { return r.json(); })
      .then(function (d) { out.textContent = d.text; out.style.color = d.ok ? 'var(--ok)' : 'var(--danger)'; })
      .catch(function () { out.textContent = 'No se pudo completar la prueba.'; out.style.color = 'var(--danger)'; })
      .then(function () { btn.classList.remove('loading'); });
  });
})();

// Device search: results update while typing (no need to press Enter).
(function () {
  var input = document.querySelector('.search input[type=search]');
  if (!input) return;
  if (input.value) { input.focus(); var v = input.value; input.value = ''; input.value = v; }
  var t;
  input.addEventListener('input', function () {
    clearTimeout(t);
    t = setTimeout(function () { input.form.submit(); }, 600);
  });
})();

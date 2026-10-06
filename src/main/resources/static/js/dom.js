export function el(tag, atributos = {}, filhos = []) {
  const no = document.createElement(tag);
  for (const [nome, valor] of Object.entries(atributos)) {
    if (valor === null || valor === undefined || valor === false) {
      continue;
    }
    if (nome === 'classe') {
      no.className = valor;
    } else if (nome === 'texto') {
      no.textContent = String(valor);
    } else if (nome === 'aoClicar') {
      no.addEventListener('click', valor);
    } else if (nome === 'aoMudar') {
      no.addEventListener('change', valor);
    } else {
      no.setAttribute(nome, String(valor));
    }
  }
  for (const filho of [].concat(filhos)) {
    if (filho === null || filho === undefined || filho === false) {
      continue;
    }
    no.appendChild(typeof filho === 'string' ? document.createTextNode(filho) : filho);
  }
  return no;
}

export function pedaco(filhos) {
  const fragmento = document.createDocumentFragment();
  for (const filho of [].concat(filhos)) {
    if (filho === null || filho === undefined || filho === false) {
      continue;
    }
    fragmento.appendChild(typeof filho === 'string' ? document.createTextNode(filho) : filho);
  }
  return fragmento;
}

export function trocar(no, conteudo) {
  no.textContent = '';
  no.appendChild(pedaco(conteudo));
}

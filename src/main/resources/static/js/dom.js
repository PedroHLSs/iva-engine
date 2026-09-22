/* ---------------------------------------------------------------------------
   Construcao de DOM sem biblioteca.

   Todo texto vindo da API entra por textContent, nunca por innerHTML. Fundamento
   normativo, motivo de nao conclusao e justificativa de tratativa sao texto que
   alguem digitou ou que o CSV do catalogo trouxe: se qualquer um deles for
   interpretado como marcacao, a pagina passa a executar conteudo de arquivo de
   entrada. E a mesma postura de visualizacao/resultados.html.
   --------------------------------------------------------------------------- */

/**
 * Cria um elemento.
 *
 * @param {string} tag
 * @param {object} atributos  classe, texto, href, title, dados e afins
 * @param {Array}  filhos     nos ou textos
 */
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

/** Fragmento com varios nos, para devolver lista sem embrulho extra. */
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

/** Substitui o conteudo de um no, sem nunca montar marcacao a partir de dado. */
export function trocar(no, conteudo) {
  no.textContent = '';
  no.appendChild(pedaco(conteudo));
}

import { el } from './dom.js';

export function recolhivel(resumo, corpo, recolhidoPorPadrao, classe) {
  const bloco = el('details', {
    classe: 'recolhivel' + (classe ? ' ' + classe : ''),
    open: recolhidoPorPadrao === true ? null : 'open',
  }, [
    el('summary', { classe: 'recolhivel-resumo' }, [].concat(resumo)),
    el('div', { classe: 'recolhivel-corpo' }, [].concat(corpo)),
  ]);
  return bloco;
}

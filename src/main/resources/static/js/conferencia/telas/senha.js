import { el, trocar } from '../../dom.js';
import { trocarSenha } from '../../sessao.js';
import { falha } from '../pecas.js';

export async function desenhar(raiz) {
  const resultado = el('div', {});
  const atual = el('input', { type: 'password', id: 'senha-atual', autocomplete: 'current-password' });
  const nova = el('input', { type: 'password', id: 'senha-nova', autocomplete: 'new-password' });
  const repetida = el('input', { type: 'password', id: 'senha-repetida', autocomplete: 'new-password' });

  const formulario = el('form', { classe: 'formulario' }, [
    el('label', { for: 'senha-atual', texto: 'Senha atual' }), atual,
    el('label', { for: 'senha-nova', texto: 'Senha nova (minimo de 12 caracteres)' }), nova,
    el('label', { for: 'senha-repetida', texto: 'Repita a senha nova' }), repetida,
    el('div', { classe: 'acoes' }, [el('button', { type: 'submit', classe: 'botao principal', texto: 'Trocar' })]),
  ]);

  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    trocar(resultado, []);
    if (nova.value !== repetida.value) {
      trocar(resultado, [el('div', { classe: 'aviso erro', texto: 'As duas senhas novas nao sao iguais.' })]);
      return;
    }
    try {
      await trocarSenha(atual.value, nova.value);
      trocar(resultado, [el('div', { classe: 'aviso', texto: 'Senha trocada.' })]);
    } catch (recusa) {
      trocar(resultado, [falha(recusa)]);
    } finally {
      atual.value = '';
      nova.value = '';
      repetida.value = '';
    }
  });

  trocar(raiz, [el('h1', { texto: 'Trocar a senha' }), formulario, resultado]);
}

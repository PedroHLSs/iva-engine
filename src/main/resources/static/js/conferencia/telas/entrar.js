import { el, trocar } from '../../dom.js';
import { entrar } from '../../sessao.js';
import { falha } from '../pecas.js';

export async function desenhar(raiz, parametros) {
  const erro = el('div', {});
  const login = el('input', { type: 'text', id: 'login', autocomplete: 'username', required: 'required' });
  const senha = el('input', {
    type: 'password', id: 'senha', autocomplete: 'current-password', required: 'required',
  });
  const botao = el('button', { type: 'submit', classe: 'botao principal', texto: 'Entrar' });

  const formulario = el('form', { classe: 'formulario' }, [
    el('label', { for: 'login', texto: 'Login' }),
    login,
    el('label', { for: 'senha', texto: 'Senha' }),
    senha,
    el('div', { classe: 'acoes' }, [botao]),
  ]);

  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    botao.disabled = true;
    trocar(erro, []);
    try {
      await entrar(login.value, senha.value);
      senha.value = '';
      const volta = parametros.get('volta');
      const destino = volta && !volta.includes('//') && !volta.includes('entrar') ? volta : 'index.html#/';
      window.location.href = destino;
      if (!destino.startsWith('tecnica.html')) {
        window.location.reload();
      }
    } catch (recusa) {
      senha.value = '';
      trocar(erro, [falha(recusa)]);
    } finally {
      botao.disabled = false;
    }
  });

  trocar(raiz, [
    el('section', { classe: 'cartao-de-entrada', 'aria-labelledby': 'titulo-entrada' }, [
      el('h1', { id: 'titulo-entrada', texto: 'Entrar' }),
      el('p', {
        classe: 'sub',
        texto: 'Cada analise, tratativa e carga de catalogo fica registrada com quem a fez. Por isso '
          + 'e preciso entrar antes de usar o sistema.',
      }),
      formulario,
      erro,
      el('p', {
        classe: 'nota',
        texto: 'Esqueceu a senha? Peca a um administrador que a redefina. Nao ha recuperacao por '
          + 'e-mail.',
      }),
    ]),
  ]);
  login.focus();
}

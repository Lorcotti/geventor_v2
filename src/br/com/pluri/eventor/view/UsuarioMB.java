package br.com.pluri.eventor.view;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;

import lombok.Getter;
import lombok.Setter;
import br.com.etechoracio.common.view.BaseMB;
import br.com.pluri.eventor.business.CidadeSB;
import br.com.pluri.eventor.business.DistritoSB;
import br.com.pluri.eventor.business.EnderecoSB;
import br.com.pluri.eventor.business.EstadoSB;
import br.com.pluri.eventor.business.EventoSB;
import br.com.pluri.eventor.business.UsuarioAtividadeSB;
import br.com.pluri.eventor.business.UsuarioSB;
import br.com.pluri.eventor.business.exception.CEPInvalidoException;
import br.com.pluri.eventor.business.exception.CPFNotValidException;
import br.com.pluri.eventor.business.exception.DDDInvalidoException;
import br.com.pluri.eventor.business.exception.LoginJaCadastradoException;
import br.com.pluri.eventor.business.exception.SenhaInvalidaException;
import br.com.pluri.eventor.model.Cidade;
import br.com.pluri.eventor.model.Endereco;
import br.com.pluri.eventor.model.Estado;
import br.com.pluri.eventor.model.Usuario;
import br.com.pluri.eventor.validator.ValidaCPF;

@Getter
@Setter
@Controller
@Scope("view")
public class UsuarioMB extends BaseMB {

	@Autowired
	private UsuarioSB usuarioSB;
	
	@Autowired
	private UsuarioAtividadeSB usuarioAtividadeSB;
	
	@Autowired
	private CidadeSB cidadeSB;
	
	@Autowired
	private EnderecoSB enderecoSB;
	
	@Autowired
	private EstadoSB estadoSB;

	@Autowired
	private DistritoSB distritoSB;
	
	@Autowired
	private EventoSB eventoSB;
	
	private ValidaCPF validaCPF = new ValidaCPF();
	
	private Endereco enderecoDoCEP;
	private String cepInformado;
	private List<Cidade> cidades;
	private List<Estado> estados;
	private List<Usuario> usuarios;
	private List<Usuario> usuariosSelecionados;
	private Usuario editUsuario = new Usuario();
	private Usuario usuarioLogado = new Usuario();
	private boolean modoConsulta;
	
	// TODO Implementar funcionalidade que após alteração de 'Nome de Login' não permitir mais realizar alteração de usuario ate um proximo login
	private boolean edicaoIndisponivel;  
	
	
	private int myeventos;
	private int inscritospen;
	private int myinscricoes;
	private String proxevent;
	
	public void doSave(){
		try{
			usuarioSB.insert(editUsuario);
			showInfoMessage("Usuario cadastrado com sucesso");
			navigate("PAGE_LOGIN");
		}catch (LoginJaCadastradoException e2){
			showErrorMessage(e2.getMessage());
			validationFailed();
		}
	}
	
	public void findAll(){
		this.usuarios = usuarioSB.findAll();
	}
	
	@PostConstruct
	public void infoUserLogado(){
		this.estados = estadoSB.findAll();
		if (getCurrentUserId()!=null){
			this.editUsuario = usuarioSB.findById(getCurrentUserId());
			this.usuarioLogado = usuarioSB.findById(getCurrentUserId());
			editUsuario.setLoginVerificado(true);
			this.modoConsulta = true;
			this.edicaoIndisponivel = false;
			this.editUsuario.setAtualizaSenha("N");
			findMyInscricoes();
			findInscritosPenMyEventos();
			getDataProxEventoDoUsuLogado();
			findMyEvento();
		}
	}
	
	public void findMyEvento(){
		this.myeventos = eventoSB.findEventosByUsuario(getCurrentUserId()).size();
	}
	
	public void findMyInscricoes(){
		this.myinscricoes = usuarioAtividadeSB.findMyInscricoes(getCurrentUserId()).size();
	}
	
	public void findInscritosPenMyEventos(){
		this.inscritospen = usuarioAtividadeSB.findIncritosNoEventoByUsuarioLogadoByStatus(getCurrentUserId(), "Pendente").size();
	}
	
	public void getDataProxEventoDoUsuLogado(){
		this.proxevent = formatarData(eventoSB.getDataProxEventoDoUsuLogado(getCurrentUserId()), "dd/MM");
	}
	
	public void preEdit() {
		if(modoConsulta) {
			this.modoConsulta = false; 
		} else {
			this.modoConsulta = true;
			this.editUsuario = usuarioSB.findById(getCurrentUserId());
			this.editUsuario.setAtualizaSenha("N");
			this.editUsuario.loginVerificado = true;
			this.cepInformado = null;
		}
	}
	
	public void setAvatarEdit(String avatar) throws SenhaInvalidaException{
		this.editUsuario.setAvatar(avatar);
		doEdit();
	}
	
	public void onEstadoChange(){
		if(editUsuario.getEstado() == null){
			cidades = new ArrayList<Cidade>();
			return;
		}
		cidades = cidadeSB.findByEstado(editUsuario.getEstado());
	}
	
	public boolean validaNumeroComplet(String numero) {
		if (numero.matches(".*\\d+.*") && numero.contains("_")) {
			return false;
		} else {
			return true;
		}
	}
	
	public void validaDDD(String numero) {
		try {
			if (!numero.contains("_")) {
				if(!numero.equals("")){
					if (numero.length() == 15) {
						validaPrimeiroDigCelular(numero);
					}
					if (!editUsuario.getCidade().equals("")){
						if(editUsuario.getCep() == null){
							boolean naoContem = true;
							List<Cidade> cidlist = new ArrayList<Cidade>();
							cidlist = cidadeSB.findByName(editUsuario.getCidade());
							List<Integer> listddd;
							for (Cidade cid : cidlist) {
								listddd = enderecoSB.findDDDInnerJoinCity(cid.getId());
								for (Integer ddd : listddd){
									if (ddd == Integer.parseInt(numero.substring(1, 3))){
										naoContem = false;
									}
								}
							}
							if(naoContem) {
								throw new DDDInvalidoException("DDD do n�mero '" + numero + "' n�o pertence a cidade selecionada: '" + editUsuario.getCidade() +  "'.");
							}
						} else {
							if(Integer.parseInt(numero.substring(1, 3)) != enderecoSB.findEnderecoByCEP(editUsuario.getCep()).getDdd()){
								throw new DDDInvalidoException("DDD do n�mero '" + numero + "' n�o pertence a cidade selecionada: '" + editUsuario.getCidade() +  "'.");
							}
						}
					}
				}
			}
		} catch (DDDInvalidoException e) {
			showInfoMessage(e.getMessage());
		}
	}
	
	public void validaDDDAfterSelCidade(){
		if(editUsuario.getCelular() != null) {
			validaDDD(editUsuario.getCelular());
			validaDDD(editUsuario.getTelefone());
		}
	}
	
	public void validaPrimeiroDigCelular(String numero) {
		try {
			if (Integer.parseInt(numero.substring(5, 6)) != 9) {
				throw new DDDInvalidoException("Primeiro digito '" + numero.substring(5, 6) + "' do n�mero '" + numero + "' � inv�lido");
			}
		} catch (DDDInvalidoException e) {
			showErrorMessage(e.getMessage());
			editUsuario.setCelular("");
		}
	}
	
	public void findEnderecoByCEP() {
		try {
			if (!cepInformado.equals("_____-___")) {
				this.enderecoDoCEP = new Endereco();
				this.enderecoDoCEP = enderecoSB.findCidadeAndEstadoByCEP(cepInformado);
				if (enderecoDoCEP != null) {
					this.editUsuario.setCep(enderecoDoCEP.getCep());
					this.editUsuario.setEstado(enderecoDoCEP.cidade.estado.getUf());
					onEstadoChange();
					this.editUsuario.setCidade(enderecoDoCEP.cidade.getName());
					this.editUsuario.setBairro(distritoSB.findById(enderecoDoCEP.getIdDistrict()).getBairro());
					this.editUsuario.setEndereco(enderecoDoCEP.getEndereco());
				} else {
					throw new CEPInvalidoException("CEP '" + cepInformado + "' n�o encontrado no Banco de Dados.\n"
							+ "Informe seu endere�o manualmente.");
				}
			}
		} catch (CEPInvalidoException e) {
			showErrorMessage(e.getMessage());
		}
	}
	
	@SuppressWarnings("static-access")
	public void validaCPFTab(String cpf) {
		try {
			if(!cpf.contains("_") && !validaCPF.isCPF(cpf.replace(".", "").replace("-", ""))) {
				throw new CPFNotValidException("O CPF '" + cpf + "' � inv�lido");
			}
			
		} catch (Exception e) {
			showErrorMessage(e.getMessage());
			editUsuario.setCpfCnpj("");
		}
	}
	
	public void verificaLoginExiste() {
		try {
			Usuario usuarioLogin = new Usuario();
			usuarioLogin = usuarioSB.findUsuarioByLogin(editUsuario.getLogin());
			
			if (!usuarioLogado.getLogin().equals(editUsuario.getLogin()) && usuarioLogin != null) {
				this.editUsuario.setLoginVerificado(false);
				throw new LoginJaCadastradoException("O nome de Login '" + editUsuario.getLogin() + "' j� est� cadastrado.");
			} else {
				this.editUsuario.setLoginVerificado(true);
			}
		} catch (LoginJaCadastradoException e) {
			showErrorMessage(e.getMessage());
			editUsuario.setLogin(usuarioLogado.getLogin());
		}
	}
	
	public void verificaAlterLogin(String login) {
		if(!login.equals(usuarioLogado.getLogin())) {
			this.editUsuario.setLoginVerificado(false);
		} else {
			this.editUsuario.setLoginVerificado(true);
		}
	}
	
	public void doEdit() throws SenhaInvalidaException {
		try {
			if (editUsuario.getAtualizaSenha().equals("N")){
				this.editUsuario.setSenha(usuarioSB.findById(getCurrentUserId()).getSenha());
			}
			usuarioSB.editeUsuario(editUsuario);	
			showInfoMessage("Seu usu�rio foi atualizado com sucesso.");
			this.editUsuario = new Usuario();
			this.editUsuario = usuarioSB.findById(getCurrentUserId());
			this.modoConsulta = true;
			this.editUsuario.setAtualizaSenha("N");
			verificaAlterLogin(editUsuario.getLogin());
		} catch (SenhaInvalidaException es) {
			showErrorMessage(es.getMessage());
		} catch (LoginJaCadastradoException e) {
			showErrorMessage(e.getMessage());
		}
	}
	
	public String formatarDataFromTela(Map<String, Object> params) {
		Date data = (Date) params.get("data");
		String formato = (String) params.get("formato");
        return formatarData(data, formato);
    }
	
	
	
}

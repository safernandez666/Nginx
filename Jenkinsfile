timestamps {

node () {

	stage ('DevSecOpsWithOutSecurity - Checkout') {
 	 checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '', url: 'https://github.com/safernandez666/Nginx']]]) 
	}
	def autoCancelled = false

	try {
	stage('DevSecOpsWithOutSecurity - Check GitLeaks') {
	    def shellReturnStatus = sh returnStatus: true, script: """ gitleaks-linux-amd64 --repo-path=$PWD/workspace/$JOB_NAME --verbose --pretty """ 
	    if(shellReturnStatus == 1) { currentBuild.result = 'UNSTABLE' } 
	    if (shellReturnStatus == 1) {
	      autoCancelled = true
	      error('Aborting the build.')
	    }
	  }
	} catch (e) {
	  if (autoCancelled) {
	    currentBuild.result = 'SUCCESS'
	    // return here instead of throwing error to keep the build "green"
	    return
	  }
	  // normal error handling
	  throw e
	}
	stage ('SDevSecOpsWithOutSecurity - OWASP-Dependency-Check') {
     	sh 'rm owasp* || true'
     	sh 'wget "https://raw.githubusercontent.com/safernandez666/Nginx/master/owasp-dependency-check.sh" '
     	sh 'chmod +x owasp-dependency-check.sh'
     	sh 'bash owasp-dependency-check.sh'
     	sh 'cat /var/lib/jenkins/OWASP-Dependency-Check/reports/dependency-check-report.xml'
    }
	stage ('DevSecOpsWithOutSecurity - Copiar Archivos a Ansible') {
		// Shell build step
		sh """ 
		cp index.php /opt/docker 
		 """		// Shell build step
    }
	stage ('DevSecOpsWithOutSecurity - Generar Build de Docker & Eliminar Conteneradores e Imagines') {
		sh """ 
		cd /opt/docker
		docker build -t nginx .
		docker tag nginx:latest safernandez666/nginx:latest
		docker push safernandez666/nginx:latest
		docker rmi nginx safernandez666/nginx 
		 """		// Shell build step
    }
	stage ('DevSecOpsWithOutSecurity - Scanneo de Imagen Docker MicroAqua') {
		aquaMicroscanner imageName: 'safernandez666/nginx:latest', notCompliesCmd: 'exit 1', onDisallowed: 'failed' 
    }	
	stage ('DevSecOpsWithOutSecurity - Deploy en Nodos de Ansible') {
		sh """ 
		cd /opt/playbooks
		ansible-playbook -i ../ansible/hosts createNginx.yml --user ubuntu --private-key ../ansible/Pipeline.pem 
		 """ 
    }	
  }
}

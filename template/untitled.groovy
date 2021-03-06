pipeline {
agent any
    options {
        buildDiscarder(logRotator(numToKeepStr: '3'))
    }
    stages {
        stage('GitHub') {
            steps {
                //slackSend (color: '#FFFF00', message: "EMPEZO: Tarea '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
                echo "Clonacion del Proyecto en GitHub"
                checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '', url: 'https://github.com/safernandez666/Nginx']]])
            }
        }
        stage('SAST') {
            parallel {
                stage('Check GitLeaks') {
                    steps { 
                    echo "Analisis Leaks"       
                        script {
                            int code = sh returnStatus: true, script: """ gitleaks --repo-path=$PWD/workspace/$JOB_NAME --verbose --pretty """
                            if(code==1) {
                                currentBuild.result = 'FAILURE' 
                                error('Contraseñas en el Codigo.')
                                println "UNESTABLE"
                            }
                            else {
                                currentBuild.result = 'SUCCESS' 
                                println "Sin Contraseñas en el Codigo."
                                println "SUCCESS"
                            }   
                        }         
                    }
                }
                stage('Dependency Check') {
                    steps {
                        echo "Analisis de Dependencias"
                        sh 'sh /opt/dependency-check/bin/dependency-check.sh --scan /var/lib/jenkins/workspace/$JOB_NAME --format ALL --nodeAuditSkipDevDependencies --disableNodeJS'
                        dependencyCheckPublisher pattern: '**/dependency-check-report.xml'
                        sleep(time:5,unit:"SECONDS")
                    }
                }
                stage('SonarQube') {
                    steps {
                        echo "Analisis SonarQube"
                        sh "/opt/sonar-scanner/bin/sonar-scanner -Dsonar.host.url=http://jenkins.local:9000 -Dsonar.projectName=DevSecOps -Dsonar.projectVersion=1.0 -Dsonar.projectKey=DevSecOps -Dsonar.sources=. -Dsonar.projectBaseDir=/var/lib/jenkins/workspace/$JOB_NAME/src"
                        sleep(time:10,unit:"SECONDS")
                    }
                }
            }
        }
        stage('Copia Fuentes') {
            steps {
                echo "Copia Fuentes a Dockerfile"
                // Shell build step
                sh """ 
                cp -R template /opt/docker/nginx
                cp -R static /opt/docker/nginx
                cp app.py /opt/docker/nginx
                cp Dockerfile /opt/docker/nginx
                 """        // Shell build step
            }
        }
        stage('Generar Build de Docker & Eliminar Conteneradores e Imagines') {
            steps {
                echo "Copia Fuentes a Dockerfile"
                sh """ 
                cd /opt/docker/nginx
                docker build -t nginx .
                docker tag nginx:latest safernandez666/nginx:latest
                docker tag nginx:latest safernandez666/nginx:v1.$BUILD_ID 
                docker push safernandez666/nginx:latest
                docker push safernandez666/nginx:v1.$BUILD_ID
                docker rmi nginx safernandez666/nginx safernandez666/nginx:v1.$BUILD_ID
                 """        // Shell build step 
            }
        }
        stage('Scan Docker MicroAqua'){
            steps {
                //aquaMicroscanner imageName: 'safernandez666/nginx:latest', notCompliesCmd: 'exit 1', onDisallowed: 'fail'
                aquaMicroscanner imageName: 'safernandez666/nginx:latest', notCompliesCmd: '', onDisallowed: 'ignore', outputFormat: 'html'
            }
        }
        stage('Deploy en Nodos de Ansible') {
            steps {
                sh """ 
                cd /opt/playbooks
                ansible-playbook -i /opt/playbooks/hosts createNginx.yml --private-key /opt/keys/key.pem -u santiago 
                """ 
            }
        }
        stage('DAST') {
            steps {
                script {
                    //sh "docker exec zap zap-cli --verbose quick-scan http://jenkins.local:8090 -l Medium" 
                    try {
                        echo "Inicio de Scanneo Dinamico"
                        sh "docker exec zap zap-cli --verbose quick-scan http://jenkins.local:8090 -l Medium" 
                        //sh "docker exec zap zap-cli --verbose alerts --alert-level Medium -f json | jq length"
                        currentBuild.result = 'SUCCESS' 
                    }
                    catch (Exception e) {
                            //echo e.getMessage() 
                            //currentBuild.result = 'FAILURE'
                            println ("Revisar Reporte ZAP. Se encontraron Vulnerabilidades.")

                        }
                    }  
                    echo currentBuild.result 
                    echo "Generacion de Reporte"
                    sh "docker exec zap zap-cli --verbose report -o /zap/reports/owasp-quick-scan-report.html --output-format html"
                    publishHTML target: [
                        allowMissing: false,
                        alwaysLinkToLastBuild: false,
                        keepAll: true,
                        reportDir: '/opt/dast/reports',
                        reportFiles: 'owasp-quick-scan-report.html',
                        reportName: 'Analisis DAST'
                      ]          
            }
        }
}
}
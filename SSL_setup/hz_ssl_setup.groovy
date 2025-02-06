pipeline {
    agent { label 'cm-linux' }
    
    stages {
        stage('SSL-Setup') {
            steps {
                script {
                    cleanWs()
                    deleteDir()

                    // Clone Hazelcast Services repository
                    git branch: "main", 
                        credentialsId: "sbiNET-G3-DEV-GITHUB-OAUTH", 
                        url: "https://alm-github.systems.uk.sbi/dtc-hazelcast/Hazelcast-Services.git"
                    
                    // Setup jq tool
                    sh "mkdir -p jqdir"
                    dir('jqdir') {
                        git branch: "master", 
                            credentialsId: "sbiNET-G3-DEV-GITHUB-OAUTH", 
                            url: "https://alm-github.systems.uk.sbi/sprintnet/jq.git"
                        sh """
                            chmod +x ./jq
                            mv ./jq ../
                        """
                    }

                    // Read environment configuration
                    def folderParts = pwd().split("/")
                    def folderName = folderParts[folderParts.length - 2]
                    
                    dir("${WORKSPACE}/${folderName}/") {
                        sh "cat Environments.groovy >> env.txt"
                        def envList = readFile('env.txt')
                        
                        properties([
                            parameters([
                                choice(name: 'Environment', choices: envList, description: 'Select Environment'),
                                string(name: 'HostName', defaultValue: '', description: 'Enter Server Name'),
                                string(name: 'ClusterName', defaultValue: '', description: 'Enter Cluster Name'),
                                string(name: 'DomainName', defaultValue: '', description: 'Enter Domain Name for SSL'),
                                string(name: 'cr_number', defaultValue: '', description: 'Enter CR Number for Production Deployment')
                            ])
                        ])
                    }

                    def templateId = "110778" // Update with actual template ID
                    def containers = "container01"
                    def jqCli = "${WORKSPACE}/jq"
                    def clusterSafeUrl = "clusterSafeUrl.json"
                    
                    def environment = params.Environment
                    def hostname = params.HostName
                    if (hostname.trim() == "") {
                        error "Error! Host_Name is Empty. Please enter a valid Host_Name value."
                    }

                    def clusterSafeURL = sh(script: "cat ${clusterSafeUrl} | ${jqCli} -r .'${environment}'", returnStdout: true).trim()
                    
                    // Prepare variables for Ansible
                    def extravars = """
                    {
                        "hostname": "${hostname}",
                        "cluster_name": "${params.ClusterName}",
                        "domain_name": "${params.DomainName}",
                        "environment": "${environment}",
                        "cluster_safe_url": "${clusterSafeURL}"
                    }
                    """
                    
                    // Production environment handling
                    if (environment.toLowerCase().startsWith("prod")) {
                        timeout(time: 120, unit: 'SECONDS') {
                            def userInput = input(
                                id: 'Input-username',
                                parameters: [
                                    string(name: 'Username', description: 'Enter Username:'),
                                    password(name: 'Password', description: 'Enter Password:')
                                ],
                                submitterParameter: 'approver'
                            )
                            
                            def userName = userInput['Username']
                            def password = userInput['Password'].toString()
                            def crNumber = params.cr_number
                            if (crNumber.trim() == "") {
                                error "Error! CR Number is Empty for Production Environment."
                            }
                            
                            extravars = """
                            {
                                "cr_number": "${crNumber}",
                                "hostname": "${hostname}",
                                "cluster_name": "${params.ClusterName}",
                                "domain_name": "${params.DomainName}",
                                "environment": "${environment}",
                                "cluster_safe_url": "${clusterSafeURL}"
                            }
                            """
                            
                            templateId = "64469" // Production template ID
                        }
                    }

                    // Trigger Ansible deployment
                    def deployments = load "${WORKSPACE}/deployment.groovy"
                    deployments.getApproval("Approve: SSL Certificate Setup", "HZ-approvers")
                    deployments.triggerAnsibleTower(templateId, environment, extravars)
                }
            }
        }
    }
}

pipeline {
    agent { label 'cm-linux' }
    
    stages {
        stage('Server-Restart') { 
            steps {
                script {
                    cleanWs()
                    deleteDir()
                    
                    def userName = ""
                    def password = ""

                    def getFolder = pwd().split("/")
                    def foldername = getFolder[getFolder.length - 2]

                    // Cloning repositories
                    git branch: "main", credentialsId: "HSBCNET-G3-DEV-GITHUB-OAUTH", url: "https://alm-github.systems.uk.hsbc/dtc-hazelcast/Hazelcast-Services.git"
                    
                    sh("mkdir jqdir")
                    
                    dir('jqdir') {
                        git branch: "master", credentialsId: "HSBCNET-G3-DEV-GITHUB-OAUTH", url: "https://alm-github.systems.uk.hsbc/sprintnet/jq.git"
                        
                        sh """
                            chmod +x ./jq
                            mv ./jq ../
                        """
                    }
                    
                    dir("${WORKSPACE}/${foldername}/") {
                        sh """
                            { set +x; } 2>/dev/null;
                            cat Environments.groovy >> env.txt
                        """
                        
                        def envList = readFile 'env.txt'

                        properties([
                            parameters([
                                choice(
                                    name: 'Environment',
                                    choices: envList.split("\n").toList(),
                                    description: 'Select Environment'
                                ),
                                choice(
                                    name: 'Action',
                                    choices: ['start', 'stop', 'restart'],
                                    description: 'Select Action'
                                ),
                                choice(
                                    name: 'Mancenter',
                                    choices: ['true', 'false'],
                                    description: 'Enable Mancenter (true/false)'
                                ),
                                string(
                                    name: 'Host_Name',
                                    defaultValue: '',
                                    description: 'Enter Server Name'
                                ),
                                string(
                                    name: 'cr_number',
                                    defaultValue: '',
                                    description: 'Enter CR Number for Production'
                                )
                            ])
                        ])
                    }

                    // Validate input parameters
                    def Hostname = "${params.Host_Name}"
                    if (Hostname.trim() == "") {
                        error "Error! Host_Name is Empty. Please enter Host_Name."
                    }

                    def environment = "${params.Environment}"
                    def extravars = """{
                        "hostname": "${params.Host_Name}",
                        "action": "${params.Action}",
                        "mancenter": "${params.Mancenter}"
                    }"""

                    // Handling production environment
                    if (environment.toLowerCase().startsWith("prod")) {
                        timeout(time: 120, unit: 'SECONDS') {
                            def userInput = input(id: 'Input-username',
                                parameters: [
                                    string(name: 'Username', defaultValue: '', description: 'Enter Username'),
                                    password(name: 'Password', description: 'Enter Password')
                                ]
                            )
                            userName = userInput['Username']
                            password = userInput['Password'].toString()
                        }

                        def cr_number = "${params.cr_number}"
                        if (cr_number.trim() == "") {
                            error "Error! CR Number is Empty for Production."
                        }
                        extravars = """{
                            "cr_number": "${params.cr_number}",
                            "hostname": "${params.Host_Name}",
                            "action": "${params.Action}",
                            "mancenter": "${params.Mancenter}"
                        }"""
                    }

                    println("Extra-Vars: " + extravars)

                    // Triggering Ansible
                    def ansibleOutput = deployments.triggerAnsibleTower("templateID", environment, extravars, userName, password)

                    println "Ansible Output: ${ansibleOutput}"

                    if (ansibleOutput != null && ansibleOutput.contains("skipping: no hosts matched")) {
                        error "Error! No hosts matched in Ansible playbook."
                    }
                }
            }
        }
    }
}

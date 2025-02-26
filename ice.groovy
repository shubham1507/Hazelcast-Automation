pipeline {
    agent { node { label 'cm-linux' } }
    parameters {
        choice(name: 'Type', choices: ['Change_WITHOUT_SourceCode_update', 'Change_with_SourceCode_update'], description: 'Select type of CR')
        string(name: 'CRNumber', defaultValue: '', description: 'CR Number')
        string(name: 'RequirementUrls', defaultValue: '', description: 'RequirementUrls')
        string(name: 'testEvidenceUrl', defaultValue: '', description: 'Test Evidence Url')
        string(name: 'postDeploymentVerificationEvidenceUrl', defaultValue: '', description: 'Post Deployment Verification EvidenceUrl')
        string(name: 'independentCodeReviewUrl', defaultValue: '', description: 'Independent Code ReviewUrl')
        string(name: 'businessApprovalUrl', defaultValue: '', description: 'Business Approval Url')
        string(name: 'AutomatedRegressionTestUrls', defaultValue: '', description: 'AutomatedRegressionTestUrls')
        string(name: 'Source_Code_URL', defaultValue: 'NA', description: 'Add if you choose Change_with_SourceCode_update : Source_Code_URL')
    }

    stages {
        stage('UpdateEvidence') {
            steps {
                echo "${businessApprovalUrl}"
                script {
                    def response // Declare 'response' variable

                    if ("${Type}" == "Change_with_SourceCode_update") {
                        // Condition: Change with source code update
                        response = """
                            curl -i -s -X 'PATCH' \\
                            "https://ice.it.global.hsbc/ice/api/v1/changes/${CRNumber}" \\
                            -H 'accept: */*' \\
                            -H 'Authorization: Basic SUNFLXVzZXI6VmI1WHFoSVJ6UWgwSnJNYng0ejNqVXRNb0FhOXJkUWFMbWxWY0RreW0zYw==' \\
                            -H 'Content-Type: application/json' \\
                            -d '{
                                "change": {
                                    "testEvidenceUrl": "${testEvidenceUrl}",
                                    "independentCodeReviewUrl": "${independentCodeReviewUrl}",
                                    "businessApprovalUrl": "${businessApprovalUrl}",
                                    "artifacts": {
                                        "artifacts": [
                                            {
                                                "sourceCodeUrl": "${Source_Code_URL}",
                                                "componentId": "${Source_Code_URL}",
                                                "sourceCodeDiffUrl": "${Source_Code_URL}",
                                                "version": "${Source_Code_URL}",
                                                "url": "${Source_Code_URL}",
                                                "previousVersion": "${Source_Code_URL}",
                                                "componentSourceCodeUrl": "${Source_Code_URL}",
                                                "id": "${Source_Code_URL}",
                                                "codeReviewUrl": "${Source_Code_URL}"
                                            }
                                        ]
                                    },
                                    "requirementUrls": [ "${Source_Code_URL}" ],
                                    "postDeploymentVerificationEvidenceUrl": "${Source_Code_URL}",
                                    "sastScanUrl": "${Source_Code_URL}",
                                    "mastScanUrl": "${Source_Code_URL}",
                                    "vulnerabilityScanUrl": ""
                                },
                                "fieldsToUpdate": [
                                    "requirementUrls",
                                    "businessApprovalUrl",
                                    "independentCodeReviewUrl",
                                    "postDeploymentVerificationEvidenceUrl",
                                    "vulnerabilityScanUrl",
                                    "testEvidenceUrl",
                                    "sastScanUrl",
                                    "mastScanUrl",
                                    "artifacts"
                                ]
                            }'
                        """
                    } else {
                        // Condition: Change without source code update
                        response = """
                            curl -i -s -X 'PATCH' \\
                            "https://ice.it.global.hsbc/ice/api/v1/changes/${CRNumber}" \\
                            -H 'accept: */*' \\
                            -H 'Authorization: Basic SUNFLXVzZXI6VmI1WHFoSVJ6UWgwSnJNYng0ejNqVXRNb0FhOXJkUWFMbWxWY0RreW0zYw==' \\
                            -H 'Content-Type: application/json' \\
                            -d '{
                                "change": {
                                    "testEvidenceUrl": "${testEvidenceUrl}",
                                    "independentCodeReviewUrl": "${independentCodeReviewUrl}",
                                    "businessApprovalUrl": "${businessApprovalUrl}",
                                    "postDeploymentVerificationEvidenceUrl": "${postDeploymentVerificationEvidenceUrl}",
                                    "requirementUrls": [ "${RequirementUrls}" ],
                                    "automatedRegressionTestUrls": ["${AutomatedRegressionTestUrls}"]
                                },
                                "fieldsToUpdate": [
                                    "requirementUrls",
                                    "businessApprovalUrl",
                                    "independentCodeReviewUrl",
                                    "testEvidenceUrl",
                                    "postDeploymentVerificationEvidenceUrl",
                                    "automatedRegressionTestUrls"
                                ]
                            }'
                        """
                    }

                    // Execute the curl command and capture the status code
                    def status_code = sh(script: response, returnStdout: true).trim()
                    echo "HTTP response status code: ${status_code}"

                    // Check the status code and perform actions accordingly
                    if (status_code.contains("204")) {
                        // Success: Update successful
                        println("The update is successful, Thank You")
                        emailext body: '''Hi DEP Team,
                            The update was SUCCESSFUL, PLEASE RECALCULATE.
                            *****************************************************************
                            *****************************************************************

                            Please find the updated details.

                            "testEvidenceUrl": "${testEvidenceUrl}",
                            "independentCodeReviewUrl": "${independentCodeReviewUrl}",
                            "businessApprovalUrl": "${businessApprovalUrl}",
                            "sourceCodeUrl": "${Source_Code_URL}",
                            "componentId": "${Component_Name}",
                            "sourceCodeDiffUrl": "${Source_Code_diff_URL}",
                            "version": "${Version}",
                            "url": "${Artifact_URL}",
                            "previousVersion": "${Previous_Version}",
                            "componentSourceCodeUrl": "${Component_Source_code_home_page}",
                            "id": "${ID}",
                            "codeReviewUrl": "${Code_review_URL}"
                            "requirementUrls": [ "${RequirementUrls}" ],
                            "sastScanUrl": "${SAST_Scan_URL}",
                            "mastScanUrl": "${MAST_Scan_URL}",
                            "postDeploymentVerificationEvidenceUrl": "${postDeploymentVerificationEvidenceUrl}"

                        Thanks''', subject: "Ice update Report.${CRNumber}", recipientProviders: [requestor()], to: 'robin1.john@noexternalmail.hsbc.com,Durgesh.Tiwari@noexternalmail.hsbc.com'
                    } else if (status_code.contains("No CR with that number found")) {
                        // Error: CR not found
                        println("No CR with that number found")
                        error(message: "No CR with that number found.")
                        emailext body: '''Hi DEP Team,
                            The update was FAILED, No CR with that number found.
                            Error - Provided CR is invalid CR number or not present in ICE
                            *****************************************************************
                            *****************************************************************

                            Please find the updated details.

                            "testEvidenceUrl": "${testEvidenceUrl}",
                            "independentCodeReviewUrl": "${independentCodeReviewUrl}",
                            "businessApprovalUrl": "${businessApprovalUrl}",
                            "postDeploymentVerificationEvidenceUrl": "${postDeploymentVerificationEvidenceUrl}",
                            "requirementUrls": [ "${RequirementUrls}" ]

                        Thanks''', subject: "Ice update Report.${CRNumber}", recipientProviders: [requestor()], to: 'robin1.john@noexternalmail.hsbc.com,Durgesh.Tiwari@noexternalmail.hsbc.com'
                    } else {
                        // Error: Other errors
                        println("Please check the input provided and try again")
                        error(message: "Please check the input provided and try again.")
                        emailext body: '''Hi DEP Team,
                            The update was FAILED, Please check the input.
                            *****************************************************************
                            *****************************************************************

                            Please find the updated details.

                            "testEvidenceUrl": "${testEvidenceUrl}",
                            "independentCodeReviewUrl": "${independentCodeReviewUrl}",
                            "businessApprovalUrl": "${businessApprovalUrl}",
                            "postDeploymentVerificationEvidenceUrl": "${postDeploymentVerificationEvidenceUrl}",
                            "requirementUrls": [ "${RequirementUrls}" ]

                        Thanks''', subject: "Ice update Report.${CRNumber}", recipientProviders: [requestor()], to: 'robin1.john@noexternalmail.hsbc.com,Durgesh.Tiwari@noexternalmail.hsbc.com'
                    }
                }
            }
        }
    }
}

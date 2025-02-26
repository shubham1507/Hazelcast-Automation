curl -i -s -X PATCH https://ice.it.global.hsbc/ice/api/v1/changes/CHG4625477 \
  -H 'accept: */*' \
  -H 'Authorization: Basic aHpfdXNlcjowVXRqbHgtSXBpblVjRWtiYVJrNWh1YmxyMjA3SnhoNGdLU2F2XzJ0aFZR' \
  -H 'Content-Type: application/json' \
  -d '{
    "change": {
      "testEvidenceUrl": "https://wpb-confluence.systems.uk.hsbc/pages/viewpage.action?pageId=4912367130",
      "independentCodeReviewUrl": "https://wpb-confluence.systems.uk.hsbc/pages/viewpage.action?pageId=4912367130",
      "businessApprovalUrl": "https://wpb-confluence.systems.uk.hsbc/pages/viewpage.action?pageId=4912367130",
      "artifacts": {
        "artifacts": [
          {
            "sourceCodeUrl": "NA",
            "componentId": "NA",
            "sourceCodeDiffUrl": "NA",
            "version": "NA",
            "url": "NA",
            "previousVersion": "NA",
            "componentSourceCodeUrl": "NA",
            "id": "NA",
            "codeReviewUrl": "NA"
          }
        ]
      },
      "requirementUrls": [ "NA" ],
      "postDeploymentVerificationEvidenceUrl": "NA",
      "sastScanUrl": "NA",
      "mastScanUrl": "NA",
      "vulnerabilityScanUrl": "https://example.com/vulnerability-scan"
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

# This file demonstrates the duplicate block name inspection.
# The second dependency "vpc" block will be flagged as a duplicate.

dependency "vpc" {
  config_path = "../vpc"

  mock_outputs = {
    vpc_id = "vpc-123"
  }
}

dependency "vpc" {
  config_path = "../other-vpc"

  mock_outputs = {
    vpc_id = "vpc-456"
  }
}

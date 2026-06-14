# Formatter demo — Ctrl+Alt+L should produce same output as: terragrunt hcl format --file <this-file>

locals {
app_name="my-app"
    app_port=8080
environment="prod"

  config={
vpc_cidr="10.0.0.0/16"
      az_count=3
  }
}

dependency    "vpc"   {
config_path="../vpc"
    mock_outputs={
vpc_id="vpc-123"
    private_subnets=["a","b","c"]
}
}

inputs={
name=local.app_name
port=local.app_port
vpc_id=dependency.vpc.outputs.vpc_id
tags=merge({Name=local.app_name},{Env=local.environment})
subnet_list=["subnet-1","subnet-2","subnet-3"]

# Comment resets alignment group
short="a"
very_long_name="b"

# Another group
x=1
y=2
}

# Heredoc content should not be reformatted
generate "provider" {
path="provider.tf"
if_exists="overwrite_terragrunt"
contents=<<EOF
provider "aws" {
  region = "us-east-1"
}
EOF
}

# Formatter demo — run Ctrl+Alt+L then compare with: terragrunt hcl format --file examples/formatter-demo.hcl
# This file is intentionally badly formatted

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
}

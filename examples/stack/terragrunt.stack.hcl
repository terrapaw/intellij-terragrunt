# Stack definition — defines units that Terragrunt deploys together

unit "vpc" {
  source = "./modules/vpc"
  path   = "vpc"

  values = {
    vpc_name = "main-vpc"
    cidr     = "10.0.0.0/16"
    azs      = ["us-east-1a", "us-east-1b", "us-east-1c"]
  }
}

unit "app" {
  source = "./modules/app"
  path   = "app"

  values = {
    app_name = "my-service"
    port     = 8080
  }
}

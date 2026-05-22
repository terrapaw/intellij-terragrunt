unit "vpc" {
  source = "git::git@github.com:acme/modules.git//vpc?ref=v1.0"
  path   = "vpc"
  values = {
    vpc_name = "main"
    cidr     = "10.0.0.0/16"
    azs      = ["us-east-1a", "us-east-1b"]
  }
}

unit "app" {
  source = "git::git@github.com:acme/modules.git//app?ref=v1.0"
  path   = "app"
  values = {
    app_name = "my-service"
    port     = 8080
  }
}

stack "monitoring" {
  source = "git::git@github.com:acme/stacks.git//monitoring?ref=v2.0"
  path   = "monitoring"
  values = {
    enabled    = true
    alert_email = "ops@example.com"
  }
}

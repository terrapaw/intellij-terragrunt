locals {
  read = read_terragrunt_config("./test.hcl")
  a = local.read.locals.a.b.c
}

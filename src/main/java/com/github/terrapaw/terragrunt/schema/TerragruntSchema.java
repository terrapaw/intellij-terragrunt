package com.github.terrapaw.terragrunt.schema;

import java.util.*;

public class TerragruntSchema {
    public record AttrDef(String name, boolean required, String type, String description) {}
    public record BlockDef(String name, boolean hasLabel, List<AttrDef> attributes, List<String> nestedBlocks) {}
    public record FuncDef(String name, String signature, String description) {}

    private static final Map<String, BlockDef> BLOCKS = new LinkedHashMap<>();
    private static final Map<String, AttrDef> TOP_LEVEL_ATTRS = new LinkedHashMap<>();
    private static final List<FuncDef> FUNCTIONS = new ArrayList<>();
    private static final Set<String> DEPRECATED_ATTRS = new HashSet<>();

    static {
        // Blocks
        BLOCKS.put("terraform", new BlockDef("terraform", false, List.of(
                attr("source", false, "string"), attr("copy_terraform_lock_file", false, "bool"),
                attr("include_in_copy", false, "list"), attr("exclude_from_copy", false, "list")
        ), List.of("extra_arguments", "before_hook", "after_hook", "error_hook")));

        BLOCKS.put("remote_state", new BlockDef("remote_state", false, List.of(
                attr("backend", true, "string"), attr("config", false, "map"),
                attr("generate", false, "map"), attr("disable_init", false, "bool"),
                attr("disable_dependency_optimization", false, "bool"), attr("encryption", false, "map")
        ), List.of()));

        BLOCKS.put("include", new BlockDef("include", true, List.of(
                attr("path", true, "string"), attr("expose", false, "bool"),
                attr("merge_strategy", false, "string")
        ), List.of()));

        BLOCKS.put("locals", new BlockDef("locals", false, List.of(), List.of()));

        BLOCKS.put("dependency", new BlockDef("dependency", true, List.of(
                attr("config_path", true, "string"), attr("enabled", false, "bool"),
                attr("skip_outputs", false, "bool"), attr("mock_outputs", false, "map"),
                attr("mock_outputs_allowed_terraform_commands", false, "list"),
                attr("mock_outputs_merge_strategy_with_state", false, "string")
        ), List.of()));

        BLOCKS.put("dependencies", new BlockDef("dependencies", false, List.of(
                attr("paths", true, "list")
        ), List.of()));

        BLOCKS.put("generate", new BlockDef("generate", true, List.of(
                attr("path", true, "string"), attr("if_exists", true, "string"),
                attr("contents", true, "string"), attr("comment_prefix", false, "string"),
                attr("disable_signature", false, "bool"), attr("disable", false, "bool"),
                attr("if_disabled", false, "string")
        ), List.of()));

        BLOCKS.put("catalog", new BlockDef("catalog", false, List.of(
                attr("urls", false, "list"), attr("default_template", false, "string")
        ), List.of()));

        BLOCKS.put("engine", new BlockDef("engine", false, List.of(), List.of()));

        BLOCKS.put("feature", new BlockDef("feature", true, List.of(
                attr("default", true, "any")
        ), List.of()));

        BLOCKS.put("exclude", new BlockDef("exclude", false, List.of(
                attr("if", false, "bool"), attr("no_run", false, "bool"),
                attr("actions", false, "list"), attr("excludes", false, "list")
        ), List.of()));

        BLOCKS.put("errors", new BlockDef("errors", false, List.of(), List.of("retry", "ignore")));

        BLOCKS.put("unit", new BlockDef("unit", true, List.of(
                attr("source", true, "string"), attr("path", true, "string"),
                attr("values", false, "map"), attr("no_dot_terragrunt_stack", false, "bool"),
                attr("no_validation", false, "bool")
        ), List.of("autoinclude")));

        BLOCKS.put("stack", new BlockDef("stack", true, List.of(
                attr("source", true, "string"), attr("path", true, "string"),
                attr("values", false, "map"), attr("no_dot_terragrunt_stack", false, "bool"),
                attr("no_validation", false, "bool")
        ), List.of("autoinclude")));

        BLOCKS.put("autoinclude", new BlockDef("autoinclude", false, List.of(
                attr("inputs", false, "map")
        ), List.of("dependency", "feature", "errors", "generate", "remote_state")));

        // Top-level attributes
        TOP_LEVEL_ATTRS.put("inputs", attr("inputs", false, "map"));
        TOP_LEVEL_ATTRS.put("download_dir", attr("download_dir", false, "string"));
        TOP_LEVEL_ATTRS.put("prevent_destroy", attr("prevent_destroy", false, "bool"));
        TOP_LEVEL_ATTRS.put("skip", attr("skip", false, "bool"));
        TOP_LEVEL_ATTRS.put("iam_role", attr("iam_role", false, "string"));
        TOP_LEVEL_ATTRS.put("iam_assume_role_duration", attr("iam_assume_role_duration", false, "number"));
        TOP_LEVEL_ATTRS.put("iam_assume_role_session_name", attr("iam_assume_role_session_name", false, "string"));
        TOP_LEVEL_ATTRS.put("iam_web_identity_token", attr("iam_web_identity_token", false, "string"));
        TOP_LEVEL_ATTRS.put("terraform_binary", attr("terraform_binary", false, "string"));
        TOP_LEVEL_ATTRS.put("terraform_version_constraint", attr("terraform_version_constraint", false, "string"));
        TOP_LEVEL_ATTRS.put("terragrunt_version_constraint", attr("terragrunt_version_constraint", false, "string"));
        TOP_LEVEL_ATTRS.put("retry_max_attempts", attr("retry_max_attempts", false, "number"));
        TOP_LEVEL_ATTRS.put("retry_sleep_interval_sec", attr("retry_sleep_interval_sec", false, "number"));
        TOP_LEVEL_ATTRS.put("retryable_errors", attr("retryable_errors", false, "list"));

        // Deprecated attributes
        DEPRECATED_ATTRS.add("mock_outputs_merge_with_state");

        // Terragrunt built-in functions
        FUNCTIONS.add(func("find_in_parent_folders", "(name?, fallback?)", "Search parent dirs for a file"));
        FUNCTIONS.add(func("path_relative_to_include", "(name?)", "Relative path from include to current"));
        FUNCTIONS.add(func("path_relative_from_include", "(name?)", "Relative path from current to include"));
        FUNCTIONS.add(func("get_env", "(name, default?)", "Get environment variable"));
        FUNCTIONS.add(func("get_platform", "()", "Get current OS platform"));
        FUNCTIONS.add(func("get_repo_root", "()", "Get git repo root path"));
        FUNCTIONS.add(func("get_path_from_repo_root", "()", "Path from repo root to current dir"));
        FUNCTIONS.add(func("get_path_to_repo_root", "()", "Relative path to repo root"));
        FUNCTIONS.add(func("get_terragrunt_dir", "()", "Dir of current terragrunt.hcl"));
        FUNCTIONS.add(func("get_working_dir", "()", "Dir where terraform runs"));
        FUNCTIONS.add(func("get_parent_terragrunt_dir", "(name?)", "Dir of parent terragrunt config"));
        FUNCTIONS.add(func("get_original_terragrunt_dir", "()", "Dir of original terragrunt.hcl"));
        FUNCTIONS.add(func("run_cmd", "(args...)", "Run shell command, return stdout"));
        FUNCTIONS.add(func("read_terragrunt_config", "(path, default?)", "Parse another terragrunt config"));
        FUNCTIONS.add(func("sops_decrypt_file", "(path)", "Decrypt a SOPS-encrypted file"));
        FUNCTIONS.add(func("get_terragrunt_source_cli_flag", "()", "Get --source CLI value"));
        FUNCTIONS.add(func("read_tfvars_file", "(path)", "Read a .tfvars file"));
        FUNCTIONS.add(func("mark_as_read", "(path)", "Mark file as read for queue filtering"));
        FUNCTIONS.add(func("mark_glob_as_read", "(pattern)", "Mark glob matches as read"));
        FUNCTIONS.add(func("constraint_check", "(version, constraint)", "Check version constraint"));
        FUNCTIONS.add(func("get_aws_account_id", "()", "Get AWS account ID"));
        FUNCTIONS.add(func("get_aws_account_alias", "()", "Get AWS account alias"));
        FUNCTIONS.add(func("get_aws_caller_identity_arn", "()", "Get AWS caller ARN"));
        FUNCTIONS.add(func("get_aws_caller_identity_user_id", "()", "Get AWS caller user ID"));
        FUNCTIONS.add(func("get_terraform_commands_that_need_vars", "()", "Commands accepting -var"));
        FUNCTIONS.add(func("get_terraform_commands_that_need_input", "()", "Commands accepting -input"));
        FUNCTIONS.add(func("get_terraform_commands_that_need_locking", "()", "Commands accepting -lock-timeout"));
        FUNCTIONS.add(func("get_terraform_commands_that_need_parallelism", "()", "Commands accepting -parallelism"));
        FUNCTIONS.add(func("get_terraform_command", "()", "Current terraform command"));
        FUNCTIONS.add(func("get_terraform_cli_args", "()", "Current terraform CLI args"));
        FUNCTIONS.add(func("get_default_retryable_errors", "()", "Default retryable error patterns"));
        // Common Terraform built-ins
        FUNCTIONS.add(func("format", "(fmt, args...)", "Format a string"));
        FUNCTIONS.add(func("join", "(separator, list)", "Join list elements"));
        FUNCTIONS.add(func("merge", "(maps...)", "Merge maps"));
        FUNCTIONS.add(func("lookup", "(map, key, default?)", "Lookup map key"));
        FUNCTIONS.add(func("length", "(value)", "Length of list/map/string"));
        FUNCTIONS.add(func("element", "(list, index)", "Get list element"));
        FUNCTIONS.add(func("concat", "(lists...)", "Concatenate lists"));
        FUNCTIONS.add(func("tolist", "(value)", "Convert to list"));
        FUNCTIONS.add(func("tomap", "(value)", "Convert to map"));
        FUNCTIONS.add(func("toset", "(value)", "Convert to set"));
        FUNCTIONS.add(func("tonumber", "(value)", "Convert to number"));
        FUNCTIONS.add(func("tostring", "(value)", "Convert to string"));
        FUNCTIONS.add(func("tobool", "(value)", "Convert to bool"));
        FUNCTIONS.add(func("trimspace", "(string)", "Trim whitespace"));
        FUNCTIONS.add(func("basename", "(path)", "Get filename from path"));
        FUNCTIONS.add(func("dirname", "(path)", "Get directory from path"));
        FUNCTIONS.add(func("file", "(path)", "Read file contents"));
        FUNCTIONS.add(func("fileexists", "(path)", "Check if file exists"));
        FUNCTIONS.add(func("yamldecode", "(string)", "Decode YAML string"));
        FUNCTIONS.add(func("jsondecode", "(string)", "Decode JSON string"));
        FUNCTIONS.add(func("jsonencode", "(value)", "Encode value as JSON"));
        FUNCTIONS.add(func("yamlencode", "(value)", "Encode value as YAML"));
        FUNCTIONS.add(func("try", "(expressions...)", "Try expressions, return first success"));
        FUNCTIONS.add(func("can", "(expression)", "Check if expression evaluates without error"));
        FUNCTIONS.add(func("keys", "(map)", "Get map keys"));
        FUNCTIONS.add(func("values", "(map)", "Get map values"));
        FUNCTIONS.add(func("flatten", "(list)", "Flatten nested lists"));
        FUNCTIONS.add(func("distinct", "(list)", "Remove duplicates"));
        FUNCTIONS.add(func("contains", "(list, value)", "Check if list contains value"));
        FUNCTIONS.add(func("replace", "(string, search, replace)", "Replace in string"));
        FUNCTIONS.add(func("regex", "(pattern, string)", "Regex match"));
        FUNCTIONS.add(func("split", "(separator, string)", "Split string"));
        FUNCTIONS.add(func("lower", "(string)", "Lowercase string"));
        FUNCTIONS.add(func("upper", "(string)", "Uppercase string"));
        FUNCTIONS.add(func("title", "(string)", "Title case string"));
        FUNCTIONS.add(func("substr", "(string, offset, length)", "Substring"));
        FUNCTIONS.add(func("startswith", "(string, prefix)", "Check prefix"));
        FUNCTIONS.add(func("endswith", "(string, suffix)", "Check suffix"));
        FUNCTIONS.add(func("uuid", "()", "Generate UUID"));
        FUNCTIONS.add(func("timestamp", "()", "Current timestamp"));
        FUNCTIONS.add(func("formatdate", "(format, time)", "Format date"));
        FUNCTIONS.add(func("coalesce", "(values...)", "First non-null value"));
        FUNCTIONS.add(func("compact", "(list)", "Remove empty strings"));
        FUNCTIONS.add(func("fileset", "(path, pattern)", "Find files matching pattern"));
        FUNCTIONS.add(func("abspath", "(path)", "Absolute path"));
        FUNCTIONS.add(func("pathexpand", "(path)", "Expand ~ in path"));
        FUNCTIONS.add(func("templatefile", "(path, vars)", "Render template file"));
    }

    private static AttrDef attr(String name, boolean required, String type) {
        return new AttrDef(name, required, type, "");
    }

    private static FuncDef func(String name, String sig, String desc) {
        return new FuncDef(name, sig, desc);
    }

    public static BlockDef getBlock(String name) { return BLOCKS.get(name); }
    public static Map<String, BlockDef> getAllBlocks() { return BLOCKS; }
    public static Map<String, AttrDef> getTopLevelAttributes() { return TOP_LEVEL_ATTRS; }
    public static List<FuncDef> getFunctions() { return FUNCTIONS; }
    public static boolean isDeprecated(String attrName) { return DEPRECATED_ATTRS.contains(attrName); }
    public static boolean isKnownBlock(String name) { return BLOCKS.containsKey(name); }
    public static boolean isKnownTopLevelAttr(String name) { return TOP_LEVEL_ATTRS.containsKey(name); }
}

# shellcheck shell=bash
# Sourced by the deploy scripts. Reads one KEY=value from .env the way docker compose does,
# without executing the file as shell: values such as "TutorsPoint Admin" are not shell-safe.
env_value() {
    local line value
    line=$(grep -E "^$1=" .env | tail -n 1) || return 0
    value=${line#*=}
    value=${value%$'\r'}
    if [[ "$value" =~ ^\"(.*)\"$ || "$value" =~ ^\'(.*)\'$ ]]; then
        value=${BASH_REMATCH[1]}
    fi
    printf '%s' "$value"
}

# Replaces KEY=... in .env, or appends it.
set_env_value() {
    if grep -qE "^$1=" .env; then
        sed -i "s|^$1=.*|$1=$2|" .env
    else
        printf '%s=%s\n' "$1" "$2" >> .env
    fi
}

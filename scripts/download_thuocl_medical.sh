#!/bin/bash
#
# THUOCL医学词库下载脚本 (Shell版本)
# 用于自动下载和更新清华大学开放中文词库（THUOCL）医学词库
#
# 功能：
# 1. 从指定URL下载medical.zip或medical.txt文件
# 2. 计算文件MD5哈希，与上次下载的MD5对比
# 3. 如果文件有变化，则解压（如果需要）并移动到目标目录
# 4. 支持错误重试
#
# 配置说明：
# - 目标目录：/data/dict/medical_dict.txt（可配置）
# - MD5缓存文件：/data/dict/medical_dict.md5（可配置）
# - 下载URL：可通过环境变量或参数配置
#
# 使用方式：
# 1. 直接运行：./download_thuocl_medical.sh
# 2. 配置环境变量：
#    - THUOCL_DOWNLOAD_URL: 下载URL
#    - THUOCL_TARGET_DIR: 目标目录（默认：/data/dict）
#    - THUOCL_FILENAME: 目标文件名（默认：medical_dict.txt）
# 3. 命令行参数：./download_thuocl_medical.sh --url <URL> --target-dir <目录>
#
# 依赖命令：
# - curl 或 wget (用于下载)
# - md5sum (用于计算MD5)
# - unzip (用于解压ZIP文件)
# - awk, sed, grep (文本处理)
#
# 作者：IMKQAS系统
# 版本：1.0

set -euo pipefail

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $(date '+%Y-%m:%S') - $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

# 默认配置
DEFAULT_DOWNLOAD_URL="https://thunlp.oss-cn-qingdao.aliyuncs.com/THUOCL_medical.txt"
DEFAULT_TARGET_DIR="/data/dict"
DEFAULT_FILENAME="medical_dict.txt"
DEFAULT_MD5_FILENAME="medical_dict.md5"
MAX_RETRIES=3
RETRY_DELAY=5

# 解析命令行参数
parse_args() {
    local url="${THUOCL_DOWNLOAD_URL:-}"
    local target_dir="${THUOCL_TARGET_DIR:-}"
    local filename="${THUOCL_FILENAME:-}"

    while [[ $# -gt 0 ]]; do
        case $1 in
            --url)
                url="$2"
                shift 2
                ;;
            --target-dir)
                target_dir="$2"
                shift 2
                ;;
            --filename)
                filename="$2"
                shift 2
                ;;
            --verbose|-v)
                set -x
                shift
                ;;
            --help|-h)
                show_help
                exit 0
                ;;
            *)
                log_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done

    # 设置默认值
    DOWNLOAD_URL="${url:-$DEFAULT_DOWNLOAD_URL}"
    TARGET_DIR="${target_dir:-$DEFAULT_TARGET_DIR}"
    FILENAME="${filename:-$DEFAULT_FILENAME}"
    TARGET_PATH="$TARGET_DIR/$FILENAME"
    MD5_PATH="$TARGET_DIR/$DEFAULT_MD5_FILENAME"
    TEMP_DIR="/tmp/thuocl_download_$$"
}

# 显示帮助信息
show_help() {
    cat << EOF
THUOCL医学词库下载脚本 (Shell版本)

用法: $0 [选项]

选项:
  --url URL         下载URL (默认: $DEFAULT_DOWNLOAD_URL)
  --target-dir DIR  目标目录 (默认: $DEFAULT_TARGET_DIR)
  --filename NAME   目标文件名 (默认: $DEFAULT_FILENAME)
  --verbose, -v     详细输出模式
  --help, -h        显示此帮助信息

环境变量:
  THUOCL_DOWNLOAD_URL  下载URL
  THUOCL_TARGET_DIR    目标目录
  THUOCL_FILENAME      目标文件名

示例:
  $0
  $0 --url "https://example.com/medical.zip" --target-dir "/data/dict"
  THUOCL_DOWNLOAD_URL="https://example.com/medical.txt" $0
EOF
}

# 检查命令依赖
check_dependencies() {
    local missing_deps=()

    # 检查下载工具
    if ! command -v curl &> /dev/null && ! command -v wget &> /dev/null; then
        missing_deps+=("curl 或 wget")
    fi

    # 检查MD5计算工具
    if ! command -v md5sum &> /dev/null; then
        missing_deps+=("md5sum")
    fi

    # 检查解压工具
    if ! command -v unzip &> /dev/null; then
        missing_deps+=("unzip")
    fi

    if [[ ${#missing_deps[@]} -gt 0 ]]; then
        log_error "缺少依赖命令: ${missing_deps[*]}"
        log_info "请安装以下命令："
        for dep in "${missing_deps[@]}"; do
            echo "  - $dep"
        done
        exit 1
    fi
}

# 创建目录
create_directories() {
    log_info "创建目录: $TARGET_DIR"
    mkdir -p "$TARGET_DIR" || {
        log_error "创建目录失败: $TARGET_DIR"
        exit 1
    }

    log_info "创建临时目录: $TEMP_DIR"
    mkdir -p "$TEMP_DIR" || {
        log_error "创建临时目录失败: $TEMP_DIR"
        exit 1
    }
}

# 下载文件
download_file() {
    local url="$1"
    local output_path="$2"
    local retry_count=0

    while [[ $retry_count -lt $MAX_RETRIES ]]; do
        ((retry_count++))
        log_info "开始下载文件 (尝试 $retry_count/$MAX_RETRIES): $url"

        # 使用curl或wget下载
        if command -v curl &> /dev/null; then
            if curl -L -f --connect-timeout 30 --retry 2 --progress-bar "$url" -o "$output_path"; then
                log_info "文件下载完成: $output_path"
                return 0
            else
                log_error "curl下载失败 (尝试 $retry_count/$MAX_RETRIES)"
            fi
        elif command -v wget &> /dev/null; then
            if wget --timeout=30 --tries=2 --show-progress -O "$output_path" "$url"; then
                log_info "文件下载完成: $output_path"
                return 0
            else
                log_error "wget下载失败 (尝试 $retry_count/$MAX_RETRIES)"
            fi
        fi

        if [[ $retry_count -lt $MAX_RETRIES ]]; then
            log_info "${RETRY_DELAY}秒后重试..."
            sleep $RETRY_DELAY
        fi
    done

    log_error "下载失败，已达到最大重试次数"
    return 1
}

# 计算MD5
calculate_md5() {
    local file_path="$1"
    if [[ -f "$file_path" ]]; then
        md5sum "$file_path" | awk '{print $1}'
    else
        echo ""
    fi
}

# 获取缓存的MD5
get_cached_md5() {
    if [[ -f "$MD5_PATH" ]]; then
        cat "$MD5_PATH" 2>/dev/null | tr -d '[:space:]' || echo ""
    else
        echo ""
    fi
}

# 保存MD5
save_md5() {
    local md5_value="$1"
    echo "$md5_value" > "$MD5_PATH"
    log_info "MD5值已保存到: $MD5_PATH"
}

# 解压ZIP文件
extract_zip() {
    local zip_path="$1"
    local extract_dir="$2"

    log_info "解压ZIP文件: $zip_path"

    if ! unzip -q -o "$zip_path" -d "$extract_dir"; then
        log_error "ZIP文件解压失败: $zip_path"
        return 1
    fi

    # 查找解压后的文件
    local extracted_file
    extracted_file=$(find "$extract_dir" -name "*.txt" -type f | head -1)

    if [[ -z "$extracted_file" ]]; then
        log_warn "ZIP文件中未找到.txt文件，使用第一个文件"
        extracted_file=$(find "$extract_dir" -type f | head -1)
    fi

    if [[ -n "$extracted_file" && -f "$extracted_file" ]]; then
        log_info "找到文件: $extracted_file"
        echo "$extracted_file"
        return 0
    else
        log_error "ZIP文件中未找到有效文件"
        return 1
    fi
}

# 处理下载的文件
process_downloaded_file() {
    local downloaded_path="$1"
    local file_ext="${downloaded_path##*.}"

    if [[ "${file_ext,,}" == "zip" ]]; then
        # 解压ZIP文件
        local extract_dir="$TEMP_DIR/extracted"
        mkdir -p "$extract_dir"

        local extracted_file
        extracted_file=$(extract_zip "$downloaded_path" "$extract_dir")

        if [[ -n "$extracted_file" && -f "$extracted_file" ]]; then
            echo "$extracted_file"
            return 0
        else
            return 1
        fi
    else
        # 直接使用文件
        echo "$downloaded_path"
        return 0
    fi
}

# 备份原文件
backup_original() {
    local file_path="$1"
    if [[ -f "$file_path" ]]; then
        local backup_path="${file_path}.bak.$(date +%s)"
        cp -f "$file_path" "$backup_path"
        log_info "已备份原文件到: $backup_path"
    fi
}

# 显示文件预览
show_file_preview() {
    local file_path="$1"
    if [[ -f "$file_path" && "${file_path##*.}" == "txt" ]]; then
        log_info "文件前5行预览:"
        head -5 "$file_path" | while IFS= read -r line; do
            echo "  $line"
        done
    fi
}

# 清理临时文件
cleanup() {
    if [[ -d "$TEMP_DIR" ]]; then
        log_info "清理临时目录: $TEMP_DIR"
        rm -rf "$TEMP_DIR"
    fi
}

# 主函数
main() {
    log_info "=" "THUOCL医学词库下载器启动"
    log_info "下载URL: $DOWNLOAD_URL"
    log_info "目标路径: $TARGET_PATH"
    log_info "="

    # 检查依赖
    check_dependencies

    # 创建目录
    create_directories

    # 临时文件路径
    local temp_file="$TEMP_DIR/downloaded_file"
    rm -f "$temp_file"

    # 下载文件
    if ! download_file "$DOWNLOAD_URL" "$temp_file"; then
        log_error "文件下载失败"
        cleanup
        exit 1
    fi

    if [[ ! -f "$temp_file" ]]; then
        log_error "下载的文件不存在"
        cleanup
        exit 1
    fi

    # 处理下载的文件
    local processed_file
    processed_file=$(process_downloaded_file "$temp_file")

    if [[ -z "$processed_file" || ! -f "$processed_file" ]]; then
        log_error "文件处理失败"
        cleanup
        exit 1
    fi

    # 计算MD5
    local new_md5
    new_md5=$(calculate_md5 "$processed_file")
    log_info "新文件MD5: $new_md5"

    # 获取缓存的MD5
    local cached_md5
    cached_md5=$(get_cached_md5)
    if [[ -n "$cached_md5" ]]; then
        log_info "缓存MD5: $cached_md5"
    fi

    # 检查MD5是否变化
    if [[ -n "$cached_md5" && "$new_md5" == "$cached_md5" ]]; then
        log_info "文件MD5未变化，无需更新"
        cleanup
        exit 0
    fi

    # 文件有变化，复制到目标位置
    log_info "复制文件到目标位置: $TARGET_PATH"

    # 备份原文件
    backup_original "$TARGET_PATH"

    # 复制文件
    if ! cp -f "$processed_file" "$TARGET_PATH"; then
        log_error "文件复制失败"
        cleanup
        exit 1
    fi

    # 保存新的MD5
    save_md5 "$new_md5"

    # 显示文件信息
    local file_size
    file_size=$(stat -c%s "$TARGET_PATH" 2>/dev/null || stat -f%z "$TARGET_PATH" 2>/dev/null || echo "未知")
    log_success "文件更新完成: $TARGET_PATH ($file_size bytes)"

    # 显示文件预览
    show_file_preview "$TARGET_PATH"

    # 清理临时文件
    cleanup

    log_success "THUOCL医学词库下载任务完成"
    return 0
}

# 设置陷阱，确保脚本退出时清理临时文件
trap cleanup EXIT INT TERM

# 执行主函数
parse_args "$@"
main
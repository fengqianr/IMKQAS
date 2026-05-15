#!/usr/bin/env python3
"""
THUOCL医学词库下载脚本
用于自动下载和更新清华大学开放中文词库（THUOCL）医学词库

功能：
1. 从指定URL下载medical.zip或medical.txt文件
2. 计算文件MD5哈希，与上次下载的MD5对比
3. 如果文件有变化，则解压（如果需要）并移动到目标目录
4. 支持断点续传和错误重试

配置说明：
- 目标目录：/data/dict/medical_dict.txt（可配置）
- MD5缓存文件：/data/dict/medical_dict.md5（可配置）
- 下载URL：可通过环境变量或参数配置

使用方式：
1. 直接运行：python download_thuocl_medical.py
2. 配置环境变量：
   - THUOCL_DOWNLOAD_URL: 下载URL
   - THUOCL_TARGET_DIR: 目标目录（默认：/data/dict）
   - THUOCL_FILENAME: 目标文件名（默认：medical_dict.txt）
3. 命令行参数：python download_thuocl_medical.py --url <URL> --target-dir <目录>

作者：IMKQAS系统
版本：1.0
"""

import os
import sys
import hashlib
import zipfile
import argparse
import logging
from pathlib import Path
from typing import Optional, Tuple
import urllib.request
import urllib.error
import tempfile
import time
import shutil

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# 默认配置
DEFAULT_DOWNLOAD_URL = "https://raw.githubusercontent.com/thunlp/THUOCL/master/data/THUOCL_medical.txt"
DEFAULT_TARGET_DIR = "/data/dict"
DEFAULT_FILENAME = "medical_dict.txt"
DEFAULT_MD5_FILENAME = "medical_dict.md5"
MAX_RETRIES = 3
RETRY_DELAY = 5  # 秒


class THUOCLDownloader:
    """THUOCL医学词库下载器"""

    def __init__(self, download_url: str, target_dir: str, filename: str):
        """
        初始化下载器

        Args:
            download_url: 下载URL
            target_dir: 目标目录
            filename: 目标文件名
        """
        self.download_url = download_url
        self.target_dir = Path(target_dir)
        self.filename = filename
        self.target_path = self.target_dir / filename
        self.md5_path = self.target_dir / DEFAULT_MD5_FILENAME
        self.temp_dir = Path(tempfile.gettempdir()) / "thuocl_download"

        # 创建目录
        self.target_dir.mkdir(parents=True, exist_ok=True)
        self.temp_dir.mkdir(parents=True, exist_ok=True)

    def calculate_md5(self, file_path: Path) -> str:
        """
        计算文件的MD5哈希值

        Args:
            file_path: 文件路径

        Returns:
            MD5哈希字符串
        """
        hash_md5 = hashlib.md5()
        with open(file_path, "rb") as f:
            for chunk in iter(lambda: f.read(4096), b""):
                hash_md5.update(chunk)
        return hash_md5.hexdigest()

    def get_cached_md5(self) -> Optional[str]:
        """
        获取缓存的MD5值

        Returns:
            缓存的MD5值，如果不存在则返回None
        """
        if self.md5_path.exists():
            try:
                with open(self.md5_path, "r", encoding="utf-8") as f:
                    return f.read().strip()
            except Exception as e:
                logger.warning(f"读取MD5缓存失败: {e}")
        return None

    def save_md5(self, md5_value: str):
        """
        保存MD5值到缓存文件

        Args:
            md5_value: MD5值
        """
        try:
            with open(self.md5_path, "w", encoding="utf-8") as f:
                f.write(md5_value)
            logger.info(f"MD5值已保存到: {self.md5_path}")
        except Exception as e:
            logger.error(f"保存MD5缓存失败: {e}")

    def download_file(self, url: str, output_path: Path) -> bool:
        """
        下载文件

        Args:
            url: 下载URL
            output_path: 输出路径

        Returns:
            是否成功
        """
        for attempt in range(MAX_RETRIES):
            try:
                logger.info(f"开始下载文件 (尝试 {attempt + 1}/{MAX_RETRIES}): {url}")

                # 设置请求头，模拟浏览器
                headers = {
                    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
                }

                req = urllib.request.Request(url, headers=headers)

                with urllib.request.urlopen(req, timeout=30) as response:
                    # 检查响应状态
                    if response.status != 200:
                        logger.error(f"下载失败，HTTP状态码: {response.status}")
                        continue

                    # 获取文件大小
                    file_size = int(response.headers.get('Content-Length', 0))

                    # 下载文件
                    with open(output_path, 'wb') as f:
                        downloaded = 0
                        while True:
                            chunk = response.read(8192)
                            if not chunk:
                                break
                            f.write(chunk)
                            downloaded += len(chunk)

                            # 显示进度
                            if file_size > 0:
                                percent = (downloaded / file_size) * 100
                                sys.stdout.write(f"\r下载进度: {percent:.1f}% ({downloaded}/{file_size} bytes)")
                                sys.stdout.flush()

                    if file_size > 0:
                        print()  # 换行

                    logger.info(f"文件下载完成: {output_path}")
                    return True

            except urllib.error.URLError as e:
                logger.error(f"下载失败 (URL错误): {e}")
            except Exception as e:
                logger.error(f"下载失败: {e}")

            # 重试前等待
            if attempt < MAX_RETRIES - 1:
                logger.info(f"{RETRY_DELAY}秒后重试...")
                time.sleep(RETRY_DELAY)

        return False

    def extract_zip(self, zip_path: Path, extract_dir: Path) -> Optional[Path]:
        """
        解压ZIP文件

        Args:
            zip_path: ZIP文件路径
            extract_dir: 解压目录

        Returns:
            解压后的主要文件路径，如果失败则返回None
        """
        try:
            logger.info(f"解压ZIP文件: {zip_path}")

            with zipfile.ZipFile(zip_path, 'r') as zip_ref:
                # 获取文件列表
                file_list = zip_ref.namelist()
                logger.info(f"ZIP中包含 {len(file_list)} 个文件")

                # 查找可能的文本文件
                text_files = [f for f in file_list if f.endswith('.txt')]

                if not text_files:
                    logger.warning("ZIP文件中未找到.txt文件，解压所有文件")
                    text_files = file_list

                # 解压所有文件
                zip_ref.extractall(extract_dir)

                # 返回第一个文本文件（如果有）
                if text_files:
                    main_file = extract_dir / text_files[0]
                    logger.info(f"主要文件: {main_file}")
                    return main_file
                else:
                    return None

        except zipfile.BadZipFile as e:
            logger.error(f"ZIP文件损坏: {e}")
            return None
        except Exception as e:
            logger.error(f"解压失败: {e}")
            return None

    def process_downloaded_file(self, downloaded_path: Path) -> Optional[Path]:
        """
        处理下载的文件

        Args:
            downloaded_path: 下载的文件路径

        Returns:
            处理后的文件路径，如果失败则返回None
        """
        # 检查文件扩展名
        if downloaded_path.suffix.lower() == '.zip':
            # 解压ZIP文件
            extract_dir = self.temp_dir / "extracted"
            extract_dir.mkdir(exist_ok=True)

            extracted_file = self.extract_zip(downloaded_path, extract_dir)
            if extracted_file and extracted_file.exists():
                return extracted_file
            else:
                logger.error("ZIP文件解压失败或未找到有效文件")
                return None
        else:
            # 直接使用文件
            return downloaded_path

    def run(self) -> bool:
        """
        执行下载流程

        Returns:
            是否成功（文件已更新或无需更新）
        """
        logger.info("=" * 60)
        logger.info("THUOCL医学词库下载器启动")
        logger.info(f"下载URL: {self.download_url}")
        logger.info(f"目标路径: {self.target_path}")
        logger.info("=" * 60)

        # 生成临时文件路径
        temp_file = self.temp_dir / "downloaded_file"
        if temp_file.exists():
            temp_file.unlink()

        # 下载文件
        if not self.download_file(self.download_url, temp_file):
            logger.error("文件下载失败")
            return False

        if not temp_file.exists():
            logger.error("下载的文件不存在")
            return False

        # 处理下载的文件（解压等）
        processed_file = self.process_downloaded_file(temp_file)
        if not processed_file or not processed_file.exists():
            logger.error("文件处理失败")
            return False

        # 计算MD5
        new_md5 = self.calculate_md5(processed_file)
        logger.info(f"新文件MD5: {new_md5}")

        # 获取缓存的MD5
        cached_md5 = self.get_cached_md5()
        if cached_md5:
            logger.info(f"缓存MD5: {cached_md5}")

        # 检查MD5是否变化
        if cached_md5 and new_md5 == cached_md5:
            logger.info("文件MD5未变化，无需更新")
            return True

        # 文件有变化，复制到目标位置
        try:
            logger.info(f"复制文件到目标位置: {self.target_path}")

            # 如果目标文件已存在，先备份
            if self.target_path.exists():
                backup_path = self.target_path.with_suffix(f".bak.{int(time.time())}")
                shutil.copy2(self.target_path, backup_path)
                logger.info(f"已备份原文件到: {backup_path}")

            # 复制文件
            shutil.copy2(processed_file, self.target_path)

            # 保存新的MD5
            self.save_md5(new_md5)

            # 获取文件信息
            file_size = self.target_path.stat().st_size
            logger.info(f"文件更新完成: {self.target_path} ({file_size} bytes)")

            # 如果是文本文件，显示前几行
            if self.target_path.suffix.lower() == '.txt':
                try:
                    with open(self.target_path, 'r', encoding='utf-8', errors='ignore') as f:
                        lines = [next(f).strip() for _ in range(5)]
                    logger.info("文件前5行预览:")
                    for i, line in enumerate(lines, 1):
                        logger.info(f"  {i}: {line}")
                except Exception as e:
                    logger.warning(f"无法预览文件内容: {e}")

            return True

        except Exception as e:
            logger.error(f"文件复制失败: {e}")
            return False
        finally:
            # 清理临时文件
            self.cleanup_temp_files()

    def cleanup_temp_files(self):
        """清理临时文件"""
        try:
            if self.temp_dir.exists():
                shutil.rmtree(self.temp_dir)
                logger.debug(f"临时目录已清理: {self.temp_dir}")
        except Exception as e:
            logger.warning(f"清理临时文件失败: {e}")


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description='THUOCL医学词库下载脚本')
    parser.add_argument('--url', type=str,
                       default=os.environ.get('THUOCL_DOWNLOAD_URL', DEFAULT_DOWNLOAD_URL),
                       help=f'下载URL (默认: {DEFAULT_DOWNLOAD_URL})')
    parser.add_argument('--target-dir', type=str,
                       default=os.environ.get('THUOCL_TARGET_DIR', DEFAULT_TARGET_DIR),
                       help=f'目标目录 (默认: {DEFAULT_TARGET_DIR})')
    parser.add_argument('--filename', type=str,
                       default=os.environ.get('THUOCL_FILENAME', DEFAULT_FILENAME),
                       help=f'目标文件名 (默认: {DEFAULT_FILENAME})')
    parser.add_argument('--verbose', '-v', action='store_true',
                       help='详细输出模式')

    args = parser.parse_args()

    # 设置日志级别
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)

    # 创建下载器并运行
    downloader = THUOCLDownloader(
        download_url=args.url,
        target_dir=args.target_dir,
        filename=args.filename
    )

    success = downloader.run()

    if success:
        logger.info("THUOCL医学词库下载任务完成")
        return 0
    else:
        logger.error("THUOCL医学词库下载任务失败")
        return 1


if __name__ == "__main__":
    sys.exit(main())
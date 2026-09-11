from pathlib import Path
from typing import Any

import cv2
import numpy as np
from skimage.metrics import structural_similarity


class ImageCompareError(Exception):
    """图像比较业务异常。"""


def resize_for_compare(image: np.ndarray, max_side: int = 1280) -> np.ndarray:
    """大图缩放到合适尺寸，避免 ORB/SSIM 处理过慢。"""
    height, width = image.shape[:2]
    longest = max(height, width)
    if longest <= max_side:
        return image
    scale = max_side / float(longest)
    resized = cv2.resize(
        image,
        (int(width * scale), int(height * scale)),
        interpolation=cv2.INTER_AREA,
    )
    return resized


def read_image(image_path: str | Path) -> np.ndarray:
    """兼容中文路径读取图片。"""
    path = Path(image_path)

    if not path.exists():
        raise ImageCompareError(f"图片不存在：{path}")

    image_bytes = np.fromfile(str(path), dtype=np.uint8)
    image = cv2.imdecode(image_bytes, cv2.IMREAD_COLOR)

    if image is None:
        raise ImageCompareError(f"无法读取图片：{path}")

    return image


def save_image(image_path: str | Path, image: np.ndarray) -> None:
    """兼容中文路径保存图片。"""
    path = Path(image_path)
    path.parent.mkdir(parents=True, exist_ok=True)

    suffix = path.suffix or ".jpg"
    success, encoded_image = cv2.imencode(suffix, image)

    if not success:
        raise ImageCompareError("结果图片编码失败")

    encoded_image.tofile(str(path))


def align_image(
    reference_image: np.ndarray,
    inspection_image: np.ndarray,
    max_features: int = 3000,
    keep_match_ratio: float = 0.25,
) -> tuple[np.ndarray, int]:
    """使用 ORB 特征匹配和单应性变换，将待检图对齐到标准图。"""
    reference_gray = cv2.cvtColor(reference_image, cv2.COLOR_BGR2GRAY)
    inspection_gray = cv2.cvtColor(inspection_image, cv2.COLOR_BGR2GRAY)

    orb = cv2.ORB_create(nfeatures=max_features)

    reference_keypoints, reference_descriptors = orb.detectAndCompute(
        reference_gray, None
    )
    inspection_keypoints, inspection_descriptors = orb.detectAndCompute(
        inspection_gray, None
    )

    if reference_descriptors is None or inspection_descriptors is None:
        raise ImageCompareError("图片特征不足，无法完成自动对齐")

    matcher = cv2.BFMatcher(cv2.NORM_HAMMING, crossCheck=True)

    matches = matcher.match(inspection_descriptors, reference_descriptors)

    if len(matches) < 10:
        raise ImageCompareError(f"有效特征匹配点过少：{len(matches)}")

    matches = sorted(matches, key=lambda item: item.distance)

    keep_count = max(10, int(len(matches) * keep_match_ratio))
    good_matches = matches[:keep_count]

    inspection_points = np.float32(
        [inspection_keypoints[item.queryIdx].pt for item in good_matches]
    ).reshape(-1, 1, 2)

    reference_points = np.float32(
        [reference_keypoints[item.trainIdx].pt for item in good_matches]
    ).reshape(-1, 1, 2)

    homography, inlier_mask = cv2.findHomography(
        inspection_points, reference_points, cv2.RANSAC, 5.0
    )

    if homography is None:
        raise ImageCompareError("无法计算图像对齐矩阵")

    height, width = reference_image.shape[:2]

    aligned_image = cv2.warpPerspective(
        inspection_image, homography, (width, height)
    )

    inlier_count = int(np.sum(inlier_mask)) if inlier_mask is not None else 0

    return aligned_image, inlier_count


def calculate_difference(
    reference_image: np.ndarray,
    aligned_image: np.ndarray,
    min_area: int = 300,
    threshold_value: int = 45,
    padding: int = 8,
) -> tuple[float, np.ndarray, np.ndarray, list[dict[str, Any]]]:
    """使用 SSIM 计算图像差异，提取异常区域。"""
    reference_gray = cv2.cvtColor(reference_image, cv2.COLOR_BGR2GRAY)
    aligned_gray = cv2.cvtColor(aligned_image, cv2.COLOR_BGR2GRAY)

    reference_gray = cv2.GaussianBlur(reference_gray, (5, 5), 0)
    aligned_gray = cv2.GaussianBlur(aligned_gray, (5, 5), 0)

    ssim_score, difference_map = structural_similarity(
        reference_gray, aligned_gray, full=True
    )

    difference_map = ((1.0 - difference_map) * 255).clip(0, 255).astype(np.uint8)

    _, binary_mask = cv2.threshold(
        difference_map, threshold_value, 255, cv2.THRESH_BINARY
    )

    close_kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (7, 7))
    binary_mask = cv2.morphologyEx(
        binary_mask, cv2.MORPH_CLOSE, close_kernel, iterations=2
    )

    open_kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (3, 3))
    binary_mask = cv2.morphologyEx(
        binary_mask, cv2.MORPH_OPEN, open_kernel, iterations=1
    )

    contours, _ = cv2.findContours(
        binary_mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE
    )

    marked_image = aligned_image.copy()
    regions: list[dict[str, Any]] = []

    image_height, image_width = aligned_image.shape[:2]

    valid_index = 1

    for contour in contours:
        contour_area = cv2.contourArea(contour)

        if contour_area < min_area:
            continue

        x, y, width, height = cv2.boundingRect(contour)

        x1 = max(0, x - padding)
        y1 = max(0, y - padding)
        x2 = min(image_width, x + width + padding)
        y2 = min(image_height, y + height + padding)

        region_difference = difference_map[y1:y2, x1:x2]

        region_score = float(np.mean(region_difference) / 255.0)

        cv2.rectangle(marked_image, (x1, y1), (x2, y2), (0, 0, 255), 3)

        label = f"R{valid_index} {region_score:.2f}"

        cv2.putText(
            marked_image,
            label,
            (x1, max(25, y1 - 8)),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.65,
            (0, 0, 255),
            2,
            cv2.LINE_AA,
        )

        regions.append(
            {
                "regionId": f"R{valid_index:03d}",
                "x": int(x1),
                "y": int(y1),
                "width": int(x2 - x1),
                "height": int(y2 - y1),
                "area": round(float(contour_area), 2),
                "score": round(region_score, 4),
                "status": "PENDING",
            }
        )

        valid_index += 1

    return float(ssim_score), difference_map, marked_image, regions


def compare_images(
    reference_path: str | Path,
    inspection_path: str | Path,
    result_path: str | Path,
    mask_path: str | Path,
    min_area: int = 300,
    threshold_value: int = 45,
) -> dict[str, Any]:
    """完整的标准图与待检图对比流程。"""
    reference_image = resize_for_compare(read_image(reference_path))
    inspection_image = resize_for_compare(read_image(inspection_path))

    aligned_image, match_count = align_image(reference_image, inspection_image)

    ssim_score, difference_map, marked_image, regions = calculate_difference(
        reference_image=reference_image,
        aligned_image=aligned_image,
        min_area=min_area,
        threshold_value=threshold_value,
    )

    save_image(result_path, marked_image)
    save_image(mask_path, difference_map)

    abnormal_score = round(1.0 - ssim_score, 4)

    return {
        "isAbnormal": len(regions) > 0,
        "similarity": round(ssim_score, 4),
        "abnormalScore": abnormal_score,
        "matchedFeatureCount": match_count,
        "regionCount": len(regions),
        "regions": regions,
    }

from pathlib import Path
from shutil import copyfileobj
from uuid import uuid4

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from image_compare import ImageCompareError, compare_images

BASE_DIR = Path(__file__).resolve().parent
UPLOAD_DIR = BASE_DIR / "uploads"
RESULT_DIR = BASE_DIR / "results"

UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
RESULT_DIR.mkdir(parents=True, exist_ok=True)

app = FastAPI(title="轻量级质检图像对比服务", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.mount("/results", StaticFiles(directory=RESULT_DIR), name="results")


def save_upload_file(upload_file: UploadFile, target_path: Path) -> None:
    with target_path.open("wb") as output_file:
        copyfileobj(upload_file.file, output_file)


@app.get("/health")
def health_check() -> dict:
    return {"status": "UP", "service": "light-inspection-service"}


@app.post("/api/inspection/compare")
def compare_inspection_images(
    referenceImage: UploadFile = File(...),
    inspectionImage: UploadFile = File(...),
    minArea: int = 300,
    threshold: int = 45,
) -> dict:
    """上传标准样图和待检图，返回差异区域坐标及结果图片。"""
    for uploaded_file in [referenceImage, inspectionImage]:
        if (
            uploaded_file.content_type is None
            or not uploaded_file.content_type.startswith("image/")
        ):
            raise HTTPException(
                status_code=400, detail="标准图和待检图必须是图片"
            )

    task_id = uuid4().hex

    reference_suffix = (
        Path(referenceImage.filename or "reference.jpg").suffix or ".jpg"
    )
    inspection_suffix = (
        Path(inspectionImage.filename or "inspection.jpg").suffix or ".jpg"
    )

    reference_path = UPLOAD_DIR / f"{task_id}_reference{reference_suffix}"
    inspection_path = UPLOAD_DIR / f"{task_id}_inspection{inspection_suffix}"

    result_filename = f"{task_id}_result.jpg"
    mask_filename = f"{task_id}_difference.jpg"

    result_path = RESULT_DIR / result_filename
    mask_path = RESULT_DIR / mask_filename

    try:
        save_upload_file(referenceImage, reference_path)
        save_upload_file(inspectionImage, inspection_path)

        comparison_result = compare_images(
            reference_path=reference_path,
            inspection_path=inspection_path,
            result_path=result_path,
            mask_path=mask_path,
            min_area=minArea,
            threshold_value=threshold,
        )

        return {
            "code": 200,
            "message": "图像对比完成",
            "data": {
                "taskId": task_id,
                **comparison_result,
                "resultImageUrl": f"/results/{result_filename}",
                "differenceImageUrl": f"/results/{mask_filename}",
            },
        }

    except ImageCompareError as exception:
        raise HTTPException(status_code=422, detail=str(exception)) from exception

    except Exception as exception:
        raise HTTPException(
            status_code=500, detail=f"图像处理失败：{exception}"
        ) from exception

    finally:
        referenceImage.file.close()
        inspectionImage.file.close()

import { useState } from "react";
import { Alert, Button, Image, Modal, Spin, Typography } from "antd";
import {
  DownloadOutlined,
  EyeOutlined,
  FileImageOutlined,
  FilePdfOutlined,
  FileUnknownOutlined,
  ReloadOutlined,
} from "@ant-design/icons";
import type { RegistrationAttachment } from "../services/api";

export type RegistrationAttachmentPreviewKind = "image" | "pdf" | "unsupported";

const extension = (name: string) => {
  const separator = name.lastIndexOf(".");
  return separator > 0 && separator < name.length - 1 ? name.slice(separator + 1).toLowerCase() : "";
};

export const registrationAttachmentPreviewKind = (
  attachment: Pick<RegistrationAttachment, "contentType" | "originalName">,
): RegistrationAttachmentPreviewKind => {
  const contentType = attachment.contentType?.toLowerCase() || "";
  const fileExtension = extension(attachment.originalName);
  if (contentType.startsWith("image/") || ["jpg", "jpeg", "png", "webp"].includes(fileExtension)) {
    return "image";
  }
  if (contentType === "application/pdf" || fileExtension === "pdf") return "pdf";
  return "unsupported";
};

const formatFileSize = (size: number) => {
  if (!Number.isFinite(size) || size <= 0) return "大小未知";
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
};

const formatFileType = (attachment: Pick<RegistrationAttachment, "contentType" | "originalName">) => {
  const fileExtension = extension(attachment.originalName);
  if (fileExtension) return fileExtension.toUpperCase();
  return attachment.contentType || "未知类型";
};

export default function RegistrationAttachmentPreview({ attachment }: { attachment: RegistrationAttachment }) {
  const [pdfOpen, setPdfOpen] = useState(false);
  const [pdfLoading, setPdfLoading] = useState(true);
  const [pdfError, setPdfError] = useState(false);
  const [pdfReloadKey, setPdfReloadKey] = useState(0);
  const [imageError, setImageError] = useState(false);
  const kind = registrationAttachmentPreviewKind(attachment);
  const retryPdf = () => {
    setPdfError(false);
    setPdfLoading(true);
    setPdfReloadKey((current) => current + 1);
  };
  const fileMeta = <Typography.Text type="secondary">
    {formatFileType(attachment)} · {formatFileSize(attachment.fileSize)}
  </Typography.Text>;

  if (kind === "image" && !imageError) {
    return <div className="registration-attachment-item">
      <Image
        className="registration-attachment-thumbnail"
        width={48}
        height={48}
        src={attachment.fileUrl}
        alt={attachment.originalName}
        preview={{ mask: <EyeOutlined aria-label="预览图片" /> }}
        onError={() => setImageError(true)}
      />
      <div className="registration-attachment-meta">
        <Typography.Text ellipsis={{ tooltip: attachment.originalName }}>{attachment.originalName}</Typography.Text>
        {fileMeta}
      </div>
    </div>;
  }

  if (kind === "pdf") {
    return <>
      <div className="registration-attachment-item">
        <FilePdfOutlined className="registration-attachment-icon" aria-hidden />
        <div className="registration-attachment-meta">
          <Typography.Text ellipsis={{ tooltip: attachment.originalName }}>{attachment.originalName}</Typography.Text>
          {fileMeta}
        </div>
        <Button type="text" icon={<EyeOutlined />} onClick={() => {
          setPdfOpen(true);
          setPdfLoading(true);
          setPdfError(false);
        }}>预览</Button>
      </div>
      <Modal
        className="registration-pdf-modal"
        title={attachment.originalName}
        open={pdfOpen}
        onCancel={() => setPdfOpen(false)}
        width="min(960px, 94vw)"
        destroyOnHidden
        footer={<Button href={attachment.fileUrl} target="_blank" rel="noreferrer" icon={<DownloadOutlined />}>打开或下载</Button>}
      >
        {pdfError ? <Alert
          type="error"
          showIcon
          title="PDF 预览加载失败"
          action={<Button size="small" icon={<ReloadOutlined />} onClick={retryPdf}>重试</Button>}
        /> : <div className="registration-pdf-preview">
          {pdfLoading && <Spin tip="正在加载 PDF" />}
          <iframe
            key={pdfReloadKey}
            className={pdfLoading ? "loading" : ""}
            src={`${attachment.fileUrl}#toolbar=0&navpanes=0`}
            title={attachment.originalName}
            onLoad={() => setPdfLoading(false)}
            onError={() => { setPdfLoading(false); setPdfError(true); }}
          />
        </div>}
      </Modal>
    </>;
  }

  return <div className="registration-attachment-item">
    {imageError ? <FileImageOutlined className="registration-attachment-icon" aria-hidden />
      : <FileUnknownOutlined className="registration-attachment-icon" aria-hidden />}
    <div className="registration-attachment-meta">
      <Typography.Text ellipsis={{ tooltip: attachment.originalName }}>{attachment.originalName}</Typography.Text>
      <Typography.Text type="secondary">
        {imageError ? "图片预览加载失败" : "暂不支持应用内预览"} · {formatFileType(attachment)} · {formatFileSize(attachment.fileSize)}
      </Typography.Text>
    </div>
    <Button href={attachment.fileUrl} target="_blank" rel="noreferrer" type="text" icon={<DownloadOutlined />}>打开或下载</Button>
  </div>;
}

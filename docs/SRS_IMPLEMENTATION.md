# Đối chiếu SRS phiên bản 1.0

Tài liệu nguồn: `SRS_Android_Hoc_Tu_Vung_v1.docx`, ngày 14/10/2026. Các yêu cầu trong tài liệu được dùng làm đặc tả sản phẩm; ghi chú “nhóm cần thống nhất” không được xem là yêu cầu dừng lập trình. Bản triển khai dùng phạm vi ngoại tuyến và các giới hạn đề xuất của SRS. Thông tin nhóm phát triển chưa được cung cấp nên màn hình About không tự đặt tên thành viên.

## Truy vết chức năng

“Đã triển khai” mô tả mã và luồng hiện có, không đồng nghĩa toàn bộ tiêu chí đã được nghiệm thu trên thiết bị.

| FR | Luồng đã triển khai | Điểm kiểm tra |
| --- | --- | --- |
| 01–03 | Home, 5 mục điều hướng, tiến độ tối đa 100% | `AppStateTest`, `ActivitySmokeTest`; dữ liệu mới bằng 0, đếm vocabularyId khác nhau |
| 04 | Các phiên dở trên Home/Học tập; thứ tự, lựa chọn, vị trí, mặt thẻ lưu Room | `RepositoryTest`; Quiz dở không có điểm chính thức; checkpoint foreground mỗi 15 giây và khi pause/thao tác |
| 05 | Nguồn ôn tập từ trạng thái needsReview, ưu tiên lần sai gần nhất | Cần học lại/sai bật cờ, Đã nhớ xóa cờ; mỗi từ một bản ghi |
| 06–09 | Chủ đề, danh sách theo nguồn, chi tiết và tìm chuỗi con Anh/Việt | RecyclerView, tìm không phân biệt hoa thường, có phân biệt dấu; chi tiết không tạo tương tác |
| 10–11 | Dấu sao và ba trạng thái riêng từng tài khoản | `RepositoryTest`, `LearningRulesTest`; Quiz đúng không tự chuyển Đã thuộc |
| 12–14 | Lật Flashcard bằng chạm/nút, Trước/Tiếp, đánh giá sau lật | `ActivitySmokeTest`, `RepositoryTest`; thẻ đầu khóa Trước, chưa lật khóa đánh giá, đánh giá không ghi lặp |
| 15–16 | Hai dạng trắc nghiệm bốn phương án và điền từ; số câu 5/10/20 hoặc toàn bộ khi dưới 5 | `LearningRulesTest`, `RepositoryTest`; câu không lặp, phương án duy nhất, loại prompt nhập nhằng, Unicode/case/trim, xác nhận bỏ trống |
| 17–19 | Kết quả, chi tiết đáp án, học lại sai/bỏ trống, transaction nộp theo sessionId | `RepositoryTest`; học lại có parentSessionId, snapshot và kết quả gốc giữ nguyên |
| 20 | Nguồn Yêu thích mặc định từ dấu sao | Độc lập với thành viên danh sách tự tạo |
| 21–25 | Tạo/đổi tên danh sách; thêm/bỏ thành viên; học; xác nhận xóa theo tên | `RepositoryTest`; unique tên chuẩn hóa theo tài khoản, thành viên không trùng, xóa nguồn hủy khả năng tiếp tục phiên dở |
| 26–29 | Đăng ký, đăng nhập, đăng xuất, đổi mật khẩu | `RepositoryTest`, `ActivitySmokeTest`, `LearningRulesTest`; salt/hash, email chuẩn hóa, khóa 60 giây sau 5 lần sai, Back không mở lại tài khoản đã thoát |
| 30 | Hồ sơ tên/email; chỉ sửa tên | Kiểm tra 1–50 ký tự, lưu thành công mới cập nhật |
| 31 | Lịch sử mới nhất trước, lọc chế độ/khoảng ngày, đọc snapshot | Nguồn đã xóa không làm mất chi tiết phiên hoàn thành |
| 32–33 | Streak hiện tại/cao nhất, 4 huy hiệu, số từ/Quiz, điểm trung bình và tỷ lệ chung riêng, thời lượng ngày/tuần | `LearningRulesTest`, `AppStateTest`; 80% và 50% có trung bình 65%, tỷ lệ chung 60% |
| 34–36 | Ba theme, hai ngôn ngữ, mục tiêu 1–100 | AppCompat locales/theme; chuỗi Việt/Anh trong resources, state biểu mẫu qua ViewModel |
| 37 | Bật/tắt, giờ địa phương, quyền thông báo, lịch inexact duy nhất, phục hồi sau boot/đổi giờ/múi giờ | `ReminderTest`; thay giờ thay lịch, đăng xuất hủy lịch; kiểm thử delivery/reboot thật còn cần thiết |
| 38 | Xóa lịch sử, tương tác, trạng thái học, checkpoint và huy hiệu trong transaction | `RepositoryTest`; giữ tài khoản, cài đặt, dấu sao, danh sách và kho từ |
| 39 | About lấy versionName từ PackageManager | Mô tả ứng dụng, không tự gán giấy phép/tên nhóm |

## Các quyết định dữ liệu

- `Records` chứa User, Settings, UserWord, Collection, Membership, Session, Item, Interaction, Achievement và ActiveTime.
- Checkpoint (`currentIndex`, `cardFace`) được lưu cùng bản ghi Session thay vì bảng SessionCheckpoint riêng, để cập nhật vị trí/thời lượng nguyên tử. Đáp án tạm và snapshot phương án nằm trong Item.
- Các trường snapshot của phiên/thẻ tách khỏi từ gốc; nguồn dùng tên lúc bắt đầu. Hai tài khoản chỉ có dữ liệu mẫu chung, không chia sẻ tiến độ hoặc bộ sưu tập.
- Câu Quiz bỏ trống tính sai và được gợi ý ôn/học lại, nhưng không tăng bộ đếm “tương tác học hợp lệ” vì chưa trả lời (BR-02).
- Thời lượng chỉ lấy đồng hồ monotonic khi màn hình học foreground. Khoảng thời gian được checkpoint theo ngày địa phương; đổi múi giờ không viết lại ngày tương tác đã lưu. Quiz dở có đáp án checkpoint không làm tăng số Quiz/điểm; có thể đóng góp thời lượng hoạt động đã checkpoint.
- Bảng v1 được giữ nguyên khi nâng cấp. Không gán dữ liệu cũ không xác định tài khoản cho người đăng ký mới.
- Bộ dữ liệu demo có 60 từ/6 chủ đề; phiên âm, loại từ và ví dụ đang để trống và được hiển thị theo fallback yêu cầu. Có thể bổ sung dataset được nhóm duyệt sau mà không thay đổi luồng học.

## Checklist nghiệm thu trên thiết bị

| TC | Truy vết | Bước/điều kiện chính | Kết quả mong đợi | Trạng thái |
| --- | --- | --- | --- | --- |
| TC-01 | FR-01–05 | Tài khoản mới, học 1 từ hai lần; đóng mở app; đổi ngày | Đếm từ khác nhau đúng; tiếp tục vị trí cũ | Có test nghiệp vụ/checkpoint; cần chạy thiết bị |
| TC-02 | FR-06–11 | Tìm `technology`/`TECHNOLOGY` trong chủ đề, đổi tài khoản | Kết quả cùng phạm vi; sao/trạng thái riêng | Có test cách ly; cần test tìm/UI thiết bị |
| TC-03 | FR-12–14 | Chưa lật, lật, đánh giá, xoay, Back/Hủy | Khóa đánh giá trước lật; không ghi lặp; giữ phiên | Có Robolectric cho lật/xoay/Hủy; cần kiểm tra thiết bị |
| TC-04 | FR-15–19 | Nguồn 1/3/4 từ; nghĩa trùng; bỏ trống; nộp lặp | Không tạo câu lỗi; tính điểm đúng; một kết quả | Có test tự động; cần tương tác ba dạng trên thiết bị |
| TC-05 | FR-20–25 | Tên Unicode/trùng; thêm trùng; hai danh sách; xóa nguồn dở | Không mất từ/sao/lịch sử; phiên dở không tiếp tục | Có test repository; cần UI thiết bị |
| TC-06 | FR-26–30 | Mật khẩu 7/8/64/65 ký tự, 5 lần sai, đổi mật khẩu, thoát/Back | Ràng buộc/khóa đúng; mật khẩu mới đăng nhập được | Có test tự động; cần đo PBKDF2 <1 giây trên thiết bị tham chiếu |
| TC-07 | FR-31–33 | 4/5 và 5/10; nhiều tương tác một từ; foreground/background | Trung bình 65%, tỷ lệ 60%; nền không cộng thời gian | Có test công thức; cần đo thời gian/nền/tuần trên thiết bị |
| TC-08 | FR-34–37 | Sáng/tối/hệ thống, Việt/Anh; goal 0/1/100/101; từ chối quyền; reboot | Không mất dữ liệu; không tạo hai lịch; thông báo mở Home | Có test lịch; còn kiểm tra quyền API 33/37, reboot/delivery thật |
| TC-09 | FR-38–39 | Hủy/xác nhận đặt lại; đối chiếu version | Giữ sao/danh sách/cài đặt; chỉ xóa dữ liệu tài khoản hiện tại | Có test reset; phiên bản lấy trực tiếp từ build, cần kiểm tra thiết bị |
| TC-10 | NFR-01,05,06,07,09 | API 24/33/37; 360dp/font130%; TalkBack; airplane; 2.000 từ/10.000 tương tác | Không crash/che nút; ngoại tuyến; 95% thao tác <3 giây | Chưa nghiệm thu trên thiết bị tham chiếu |

Chưa tuyên bố nghiệm thu đầy đủ 39 FR/NFR: cloud không có thiết bị Android hoặc emulator khả dụng. Các kiểm thử Robolectric xác minh logic và một số vòng đời/UI, không xác minh chính sách thông báo, khả năng tiếp cận hay hiệu năng của hệ điều hành trên phần cứng thật.

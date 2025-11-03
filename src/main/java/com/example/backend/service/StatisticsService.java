package com.example.backend.service;

import com.example.backend.dto.BestSellingProductDTO;
import com.example.backend.dto.BrandStatisticsDTO;
import com.example.backend.dto.ChannelStatisticsDTO;
import com.example.backend.dto.LowStockProductDTO;
import com.example.backend.dto.OrderStatusStatisticsDTO;
import com.example.backend.dto.PeriodStatisticsDTO;
import com.example.backend.dto.WeeklyRevenueDTO;
import com.example.backend.entity.HoaDon;
import com.example.backend.entity.HoaDonChiTiet;
import com.example.backend.entity.SanPham;
import com.example.backend.repository.HoaDonChiTietRepository;
import com.example.backend.repository.HoaDonRepository;
import com.example.backend.repository.SanPhamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StatisticsService {
    
    private final HoaDonChiTietRepository hoaDonChiTietRepository;
    private final HoaDonRepository hoaDonRepository;
    private final SanPhamRepository sanPhamRepository;
    
    public StatisticsService(HoaDonChiTietRepository hoaDonChiTietRepository,
                           HoaDonRepository hoaDonRepository,
                           SanPhamRepository sanPhamRepository) {
        this.hoaDonChiTietRepository = hoaDonChiTietRepository;
        this.hoaDonRepository = hoaDonRepository;
        this.sanPhamRepository = sanPhamRepository;
    }
    
    /**
     * Lấy top sản phẩm bán chạy dựa trên số lượng và đơn giá từ hóa đơn chi tiết
     * Logic:
     * - Từ hoa_don_chi_tiet lấy: so_luong, don_gia, chi_tiet_san_pham_id
     * - Từ chi_tiet_san_pham_id lấy: mau_sac_id, san_pham_id
     * - Từ mau_sac_id lấy: tên màu sắc
     * - Từ san_pham_id lấy: ten_san_pham, kieu_dang_mu_id
     * - Từ kieu_dang_mu_id lấy: tên kiểu dáng mũ
     * Sắp xếp theo độ bán chạy = tổng số lượng bán
     */
    public List<BestSellingProductDTO> getBestSellingProducts(int limit) {
        System.out.println("========================================");
        System.out.println("🔍 [StatisticsService] Starting getBestSellingProducts with limit: " + limit);
        System.out.println("========================================");
        
        // Kiểm tra tổng số bản ghi trong database
        try {
            long totalCountAll = hoaDonChiTietRepository.count();
            System.out.println("📊 [StatisticsService] Total invoice details in database (ALL): " + totalCountAll);
            
            long totalCountExcludingCancelled = hoaDonChiTietRepository.countAllExcludingCancelled();
            System.out.println("📊 [StatisticsService] Total invoice details (excluding cancelled): " + totalCountExcludingCancelled);
        } catch (Exception e) {
            System.err.println("⚠️ [StatisticsService] Could not count records: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Thử lấy tất cả hóa đơn chi tiết trước (không filter) để kiểm tra có dữ liệu không
        List<HoaDonChiTiet> chiTietList = new java.util.ArrayList<>();
        
        // Bước 1: Thử lấy tất cả không filter thời gian
        try {
            System.out.println("📋 [StatisticsService] Step 1: Trying to fetch all invoice details (no date filter, excluding cancelled)...");
            chiTietList = hoaDonChiTietRepository.findAllWithProductDetailsExcludingCancelled();
            System.out.println("✅ [StatisticsService] Step 1 SUCCESS: Found " + chiTietList.size() + " invoice detail records");
            
            // Nếu không có dữ liệu, thử query backup
            if (chiTietList.isEmpty()) {
                System.out.println("⚠️ [StatisticsService] Step 1 returned empty, trying backup query...");
                try {
                    chiTietList = hoaDonChiTietRepository.findAllWithProductDetailsExcludingCancelledBackup();
                    System.out.println("✅ [StatisticsService] Backup query SUCCESS: Found " + chiTietList.size() + " invoice detail records");
                } catch (Exception e3) {
                    System.err.println("⚠️ [StatisticsService] Backup query failed: " + e3.getMessage());
                    
                    // Thử lấy tất cả không filter gì cả (kể cả cancelled)
                    try {
                        List<HoaDonChiTiet> allRecords = hoaDonChiTietRepository.findAllWithAllDetails();
                        System.out.println("📊 [StatisticsService] Found " + allRecords.size() + " invoice detail records (ALL statuses)");
                        
                        if (!allRecords.isEmpty()) {
                            System.out.println("   ⚠️ All invoices might be cancelled, or query condition has issue");
                            System.out.println("   💡 Consider using allRecords if needed (commented out for now)");
                        }
                    } catch (Exception e4) {
                        System.err.println("⚠️ [StatisticsService] Could not fetch all records: " + e4.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ [StatisticsService] Step 1 FAILED: Error in findAll query");
            System.err.println("   Error message: " + e.getMessage());
            System.err.println("   Error class: " + e.getClass().getName());
            e.printStackTrace();
            
            // Nếu query trên lỗi, thử query với date filter
            try {
                LocalDateTime endDate = LocalDateTime.now();
                LocalDateTime startDate = endDate.minusYears(1);
                System.out.println("🔄 [StatisticsService] Step 1 Fallback: Trying with date filter from " + startDate + " to " + endDate);
                chiTietList = hoaDonChiTietRepository.findWithProductDetailsByDateRange(startDate, endDate);
                System.out.println("✅ [StatisticsService] Step 1 Fallback SUCCESS: Found " + chiTietList.size() + " invoice detail records");
            } catch (Exception e2) {
                System.err.println("❌ [StatisticsService] Step 1 Fallback FAILED: Error in date filter query");
                System.err.println("   Error message: " + e2.getMessage());
                e2.printStackTrace();
                return new java.util.ArrayList<>();
            }
        }
        
        if (chiTietList.isEmpty()) {
            System.out.println("⚠️ [StatisticsService] No invoice details found in database!");
            System.out.println("   Possible reasons:");
            System.out.println("   1. Database is empty - no data in hoa_don_chi_tiet table");
            System.out.println("   2. All invoices are cancelled (trangThai = 'DA_HUY')");
            System.out.println("   3. Query conditions are too restrictive");
            System.out.println("   4. JOIN FETCH might not be loading relationships properly");
            System.out.println("");
            System.out.println("   💡 Suggestion: Check database directly:");
            System.out.println("      SELECT COUNT(*) FROM hoa_don_chi_tiet;");
            System.out.println("      SELECT COUNT(*) FROM hoa_don WHERE trang_thai != 'DA_HUY';");
            System.out.println("========================================");
            return new java.util.ArrayList<>();
        }
        
        System.out.println("✅ [StatisticsService] Step 2: Processing " + chiTietList.size() + " invoice detail records...");
        
        // Nhóm theo chi_tiet_san_pham_id và tính tổng số lượng
        Map<Long, BestSellingProductDTO> productMap = new HashMap<>();
        int skippedCount = 0;
        int processedCount = 0;
        
        System.out.println("📦 [StatisticsService] Step 3: Grouping products by chiTietSanPhamId...");
        
        for (HoaDonChiTiet hdct : chiTietList) {
            processedCount++;
            
            // Kiểm tra null
            if (hdct == null) {
                System.out.println("⚠️ [StatisticsService] Record #" + processedCount + ": hdct is null");
                skippedCount++;
                continue;
            }
            
            if (hdct.getChiTietSanPham() == null) {
                System.out.println("⚠️ [StatisticsService] Record #" + processedCount + " (id=" + hdct.getId() + "): chiTietSanPham is null");
                skippedCount++;
                continue;
            }
            
            if (hdct.getChiTietSanPham().getSanPham() == null) {
                Long chiTietSanPhamId = hdct.getChiTietSanPham().getId();
                System.out.println("⚠️ [StatisticsService] Record #" + processedCount + " (chiTietSanPhamId=" + chiTietSanPhamId + "): sanPham is null");
                skippedCount++;
                continue;
            }
            
            Long chiTietSanPhamId = hdct.getChiTietSanPham().getId();
            
            if (!productMap.containsKey(chiTietSanPhamId)) {
                // Tạo mới DTO nếu chưa có
                var chiTietSP = hdct.getChiTietSanPham();
                var sanPham = chiTietSP.getSanPham();
                
                // Lấy màu sắc từ chi_tiet_san_pham -> mau_sac_id -> mau_sac.ten_mau
                String mauSac = null;
                if (chiTietSP.getMauSac() != null) {
                    mauSac = chiTietSP.getMauSac().getTenMau();
                }
                
                // Lấy tên sản phẩm từ san_pham.ten_san_pham
                String tenSanPham = sanPham.getTenSanPham();
                
                // Lấy kiểu dáng mũ từ san_pham -> kieu_dang_mu_id -> kieu_dang_mu.ten_kieu_dang
                String kieuDang = null;
                if (sanPham.getKieuDangMu() != null) {
                    kieuDang = sanPham.getKieuDangMu().getTenKieuDang();
                }
                
                BestSellingProductDTO dto = BestSellingProductDTO.builder()
                    .chiTietSanPhamId(chiTietSanPhamId)
                    .sanPhamId(sanPham.getId())
                    .tenSanPham(tenSanPham)
                    .mauSac(mauSac)
                    .kieuDang(kieuDang)
                    .donGia(hdct.getDonGia()) // Lấy từ hoa_don_chi_tiet.don_gia
                    .soLuongBan(0) // Sẽ được cộng dồn sau
                    .build();
                
                productMap.put(chiTietSanPhamId, dto);
            }
            
            // Cộng dồn số lượng từ hoa_don_chi_tiet.so_luong
            BestSellingProductDTO dto = productMap.get(chiTietSanPhamId);
            dto.setSoLuongBan(dto.getSoLuongBan() + hdct.getSoLuong());
        }
        
        System.out.println("📈 [StatisticsService] Step 4: Processing summary");
        System.out.println("   - Total records processed: " + chiTietList.size());
        System.out.println("   - Records skipped: " + skippedCount);
        System.out.println("   - Product groups created: " + productMap.size());
        
        if (productMap.isEmpty()) {
            System.out.println("⚠️ [StatisticsService] No valid products after processing!");
            System.out.println("   All records were skipped. Possible issues:");
            System.out.println("   1. chiTietSanPham relationships are not loaded");
            System.out.println("   2. sanPham relationships are not loaded");
            System.out.println("   3. Data integrity issues in database");
            return new java.util.ArrayList<>();
        }
        
        // Sắp xếp theo số lượng bán giảm dần và lấy top
        List<BestSellingProductDTO> result = productMap.values().stream()
            .sorted((a, b) -> Integer.compare(b.getSoLuongBan(), a.getSoLuongBan()))
            .limit(limit)
            .collect(Collectors.toList());
        
        System.out.println("✅ [StatisticsService] Returning " + result.size() + " best selling products:");
        for (int i = 0; i < result.size(); i++) {
            BestSellingProductDTO dto = result.get(i);
            System.out.println("   " + (i + 1) + ". " + dto.getTenSanPham() + 
                             " | Màu: " + (dto.getMauSac() != null ? dto.getMauSac() : "N/A") + 
                             " | Kiểu: " + (dto.getKieuDang() != null ? dto.getKieuDang() : "N/A") + 
                             " | SL: " + dto.getSoLuongBan() + 
                             " | Giá: " + dto.getDonGia());
        }
        
        return result;
    }
    
    /**
     * Lấy thống kê theo khoảng thời gian (ngày, tuần, tháng, năm)
     * @param period Loại khoảng thời gian: "day", "week", "month", "year"
     * @return PeriodStatisticsDTO chứa doanh thu, số sản phẩm đã bán, số đơn hàng
     */
    public PeriodStatisticsDTO getPeriodStatistics(String period) {
        System.out.println("========================================");
        System.out.println("📊 [StatisticsService] Getting period statistics for: " + period);
        System.out.println("========================================");
        
        LocalDateTime endDate;
        LocalDateTime startDate;
        
        // Xác định khoảng thời gian dựa vào period
        LocalDate today = LocalDate.now();
        
        switch (period.toLowerCase()) {
            case "day":
            case "today":
                // Hôm nay: từ đầu ngày hôm nay đến hiện tại
                startDate = today.atStartOfDay();
                endDate = LocalDateTime.now();
                break;
            case "week":
                // Tuần này: từ đầu tuần (Thứ 2) đến hiện tại
                // DayOfWeek.getValue(): Monday=1, Sunday=7
                int dayOfWeek = today.getDayOfWeek().getValue();
                startDate = today.minusDays(dayOfWeek - 1).atStartOfDay();
                endDate = LocalDateTime.now();
                break;
            case "month":
                // Tháng này: từ ngày 1 của tháng hiện tại đến đầu ngày hôm sau
                startDate = LocalDate.of(today.getYear(), today.getMonth(), 1).atStartOfDay();
                // Ngày đầu tiên của tháng tiếp theo
                endDate = today.plusMonths(1).withDayOfMonth(1).atStartOfDay();
                break;
            case "year":
                // Năm nay: từ 1/1 của năm hiện tại đến 1/1 năm sau
                startDate = LocalDate.of(today.getYear(), 1, 1).atStartOfDay();
                endDate = LocalDate.of(today.getYear() + 1, 1, 1).atStartOfDay();
                break;
            case "lastmonth":
            case "last_month":
                // Tháng trước: từ ngày 1 của tháng trước đến ngày 1 của tháng này
                LocalDate lastMonth = today.minusMonths(1);
                startDate = LocalDate.of(lastMonth.getYear(), lastMonth.getMonth(), 1).atStartOfDay();
                endDate = LocalDate.of(today.getYear(), today.getMonth(), 1).atStartOfDay();
                break;
            case "lastyear":
            case "last_year":
                // Năm trước: từ 1/1 của năm trước đến 1/1 năm này
                startDate = LocalDate.of(today.getYear() - 1, 1, 1).atStartOfDay();
                endDate = LocalDate.of(today.getYear(), 1, 1).atStartOfDay();
                break;
            default:
                System.err.println("⚠️ [StatisticsService] Invalid period: " + period + ", defaulting to month");
                // Mặc định: tháng này
                startDate = LocalDate.of(today.getYear(), today.getMonth(), 1).atStartOfDay();
                endDate = today.plusMonths(1).withDayOfMonth(1).atStartOfDay();
        }
        
        System.out.println("📅 [StatisticsService] Date range: " + startDate + " to " + endDate);
        
        // Debug: Kiểm tra tổng số hóa đơn trong DB
        long totalHoaDon = hoaDonRepository.count();
        System.out.println("📊 [StatisticsService] Total invoices in database: " + totalHoaDon);
        
        // Debug: Kiểm tra số hóa đơn không filter trạng thái
        List<HoaDon> allInPeriod = hoaDonRepository.findByNgayTaoBetween(startDate, endDate);
        System.out.println("📊 [StatisticsService] Invoices in period (all statuses): " + allInPeriod.size());
        if (!allInPeriod.isEmpty()) {
            System.out.println("   Sample invoice statuses:");
            for (int i = 0; i < Math.min(3, allInPeriod.size()); i++) {
                HoaDon hd = allInPeriod.get(i);
                System.out.println("   - Invoice #" + hd.getId() + ": status=" + hd.getTrangThai() + 
                                 ", ngayTao=" + hd.getNgayTao() + 
                                 ", thanhTien=" + hd.getThanhTien() +
                                 ", soLuongSP=" + hd.getSoLuongSanPham());
            }
        }
        
        // Lấy tất cả hóa đơn trong khoảng thời gian (trừ đơn đã hủy)
        List<HoaDon> hoaDonList = hoaDonRepository.findByNgayTaoBetweenExcludingCancelled(startDate, endDate);
        
        System.out.println("📦 [StatisticsService] Found " + hoaDonList.size() + " invoices in period (excluding cancelled)");
        
        // Tính toán thống kê
        BigDecimal doanhThu = BigDecimal.ZERO;
        Integer sanPhamDaBan = 0;
        Integer donHang = hoaDonList.size();
        
        for (HoaDon hoaDon : hoaDonList) {
            // Tính tổng thanhTien
            if (hoaDon.getThanhTien() != null) {
                doanhThu = doanhThu.add(hoaDon.getThanhTien());
                System.out.println("   💰 Adding invoice #" + hoaDon.getId() + 
                                 " - thanhTien: " + hoaDon.getThanhTien() + 
                                 " (total now: " + doanhThu + ")");
            } else {
                System.out.println("   ⚠️ Invoice #" + hoaDon.getId() + " has null thanhTien");
            }
            
            // Tính tổng soLuongSanPham
            if (hoaDon.getSoLuongSanPham() != null) {
                sanPhamDaBan += hoaDon.getSoLuongSanPham();
                System.out.println("   📦 Adding invoice #" + hoaDon.getId() + 
                                 " - soLuongSanPham: " + hoaDon.getSoLuongSanPham() + 
                                 " (total now: " + sanPhamDaBan + ")");
            } else {
                System.out.println("   ⚠️ Invoice #" + hoaDon.getId() + " has null soLuongSanPham");
            }
        }
        
        System.out.println("📊 [StatisticsService] Statistics calculated:");
        System.out.println("   - Doanh thu: " + doanhThu);
        System.out.println("   - Sản phẩm đã bán: " + sanPhamDaBan);
        System.out.println("   - Đơn hàng: " + donHang);
        System.out.println("========================================");
        
        return PeriodStatisticsDTO.builder()
                .doanhThu(doanhThu)
                .sanPhamDaBan(sanPhamDaBan)
                .donHang(donHang)
                .period(period)
                .build();
    }
    
    /**
     * Lấy tổng số hóa đơn trong database (tất cả)
     */
    public long getTotalInvoiceCount() {
        return hoaDonRepository.count();
    }
    
    /**
     * Lấy tổng số hóa đơn không bị hủy
     */
    public long getTotalInvoiceCountExcludingCancelled() {
        return hoaDonRepository.findAll().stream()
                .filter(h -> h.getTrangThai() != HoaDon.TrangThaiHoaDon.DA_HUY)
                .count();
    }
    
    /**
     * Lấy tổng số đơn hàng và tổng doanh thu của tất cả các hóa đơn
     * Logic:
     * - Số đơn hàng: Tổng số hóa đơn trong bảng hoa_don
     * - Tổng doanh thu: Tổng thành tiền (thanhTien) của tất cả các hóa đơn
     * @return PeriodStatisticsDTO với period="all" chứa tổng số đơn hàng và tổng doanh thu
     */
    public PeriodStatisticsDTO getTotalStatistics() {
        System.out.println("========================================");
        System.out.println("📊 [StatisticsService] Getting total statistics (all invoices)");
        System.out.println("========================================");
        
        // Lấy tất cả hóa đơn (không filter theo thời gian hay trạng thái)
        List<HoaDon> allHoaDon = hoaDonRepository.findAll();
        
        System.out.println("📦 [StatisticsService] Found " + allHoaDon.size() + " invoices in total");
        
        // Tính tổng số đơn hàng
        Integer totalOrders = allHoaDon.size();
        
        // Tính tổng doanh thu (thành tiền)
        BigDecimal totalRevenue = BigDecimal.ZERO;
        
        for (HoaDon hoaDon : allHoaDon) {
            if (hoaDon.getThanhTien() != null) {
                totalRevenue = totalRevenue.add(hoaDon.getThanhTien());
            }
        }
        
        System.out.println("📊 [StatisticsService] Total Statistics:");
        System.out.println("   - Tổng số đơn hàng: " + totalOrders);
        System.out.println("   - Tổng doanh thu: " + totalRevenue);
        System.out.println("========================================");
        
        return PeriodStatisticsDTO.builder()
                .donHang(totalOrders)
                .doanhThu(totalRevenue)
                .sanPhamDaBan(0) // Không tính sản phẩm đã bán cho tổng
                .period("all")
                .build();
    }
    
    /**
     * Lấy thống kê doanh thu theo tuần trong tháng hiện tại
     * @return Danh sách WeeklyRevenueDTO chứa doanh thu theo từng tuần
     */
    public List<WeeklyRevenueDTO> getWeeklyRevenueForMonth() {
        System.out.println("========================================");
        System.out.println("📈 [StatisticsService] Getting weekly revenue for current month");
        System.out.println("========================================");
        
        // Tháng hiện tại: từ ngày 1 của tháng hiện tại đến ngày 1 của tháng sau
        LocalDate today = LocalDate.now();
        LocalDate monthStart = LocalDate.of(today.getYear(), today.getMonth(), 1);
        LocalDate monthEnd = today.plusMonths(1).withDayOfMonth(1);
        
        LocalDateTime startDateTime = monthStart.atStartOfDay();
        LocalDateTime endDateTime = monthEnd.atStartOfDay();
        
        System.out.println("📅 [StatisticsService] Month range: " + startDateTime + " to " + endDateTime);
        System.out.println("📅 [StatisticsService] Current month: " + today.getMonth() + "/" + today.getYear());
        
        // Lấy tất cả hóa đơn trong tháng (trừ đơn đã hủy)
        List<HoaDon> hoaDonList = hoaDonRepository.findByNgayTaoBetweenExcludingCancelled(startDateTime, endDateTime);
        
        System.out.println("📦 [StatisticsService] Found " + hoaDonList.size() + " invoices in current month");
        
        // Chia tháng thành các tuần
        List<WeeklyRevenueDTO> weeklyRevenues = new ArrayList<>();
        LocalDate currentDate = monthStart;
        int weekNumber = 1;
        
        while (currentDate.isBefore(monthEnd)) {
            // Xác định ngày bắt đầu tuần (Thứ 2)
            LocalDate weekStart = currentDate;
            if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
                // Nếu không phải Thứ 2, tìm Thứ 2 gần nhất trước đó (hoặc giữ nguyên nếu là ngày đầu tháng)
                if (weekStart.getDayOfWeek().getValue() > DayOfWeek.MONDAY.getValue()) {
                    weekStart = weekStart.minusDays(weekStart.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
                }
                // Nếu tuần bắt đầu trước tháng, đặt về ngày đầu tháng
                if (weekStart.isBefore(monthStart)) {
                    weekStart = monthStart;
                }
            }
            
            // Xác định ngày kết thúc tuần (Chủ nhật hoặc cuối tháng)
            LocalDate weekEnd = weekStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            if (weekEnd.isAfter(monthEnd) || weekEnd.isEqual(monthEnd)) {
                weekEnd = monthEnd.minusDays(1); // Trừ 1 vì monthEnd là 1/12 (không tính)
            }
            
            // Nếu tuần không hợp lệ (weekStart > weekEnd), bỏ qua
            if (weekStart.isAfter(weekEnd)) {
                break;
            }
            
            System.out.println("📅 [StatisticsService] Week " + weekNumber + ": " + weekStart + " to " + weekEnd);
            
            // Tính tổng doanh thu và số đơn hàng trong tuần này
            BigDecimal weekRevenue = BigDecimal.ZERO;
            int weekOrders = 0;
            
            for (HoaDon hoaDon : hoaDonList) {
                LocalDate invoiceDate = hoaDon.getNgayTao().toLocalDate();
                
                // Kiểm tra xem hóa đơn có thuộc tuần này không
                if (!invoiceDate.isBefore(weekStart) && !invoiceDate.isAfter(weekEnd)) {
                    if (hoaDon.getThanhTien() != null) {
                        weekRevenue = weekRevenue.add(hoaDon.getThanhTien());
                    }
                    weekOrders++;
                }
            }
            
            System.out.println("   💰 Week " + weekNumber + " revenue: " + weekRevenue + ", orders: " + weekOrders);
            
            weeklyRevenues.add(WeeklyRevenueDTO.builder()
                    .weekLabel("Tuần " + weekNumber)
                    .startDate(weekStart)
                    .endDate(weekEnd)
                    .totalRevenue(weekRevenue)
                    .totalOrders(weekOrders)
                    .build());
            
            // Chuyển sang tuần tiếp theo (bắt đầu từ ngày sau Chủ nhật)
            currentDate = weekEnd.plusDays(1);
            weekNumber++;
            
            // Nếu đã vượt quá cuối tháng, dừng lại
            if (currentDate.isAfter(monthEnd) || currentDate.isEqual(monthEnd)) {
                break;
            }
        }
        
        System.out.println("✅ [StatisticsService] Returning " + weeklyRevenues.size() + " weeks of revenue data");
        System.out.println("========================================");
        
        return weeklyRevenues;
    }
    
    /**
     * Lấy top 3 nhà sản xuất bán chạy nhất dựa trên tổng số lượng mua
     * Logic:
     * - Từ hoa_don_chi_tiet lấy: so_luong, chi_tiet_san_pham_id
     * - Từ chi_tiet_san_pham lấy: san_pham_id
     * - Từ san_pham lấy: nha_san_xuat_id
     * - Từ nha_san_xuat lấy: ten_nha_san_xuat
     * - Tính tổng soLuong theo nha_san_xuat_id
     * - Sắp xếp giảm dần và lấy top 3
     */
    public List<BrandStatisticsDTO> getTopBrandsByPurchaseCount(int limit) {
        System.out.println("========================================");
        System.out.println("🏭 [StatisticsService] Getting top " + limit + " brands by purchase count");
        System.out.println("========================================");
        
        // Lấy tất cả hóa đơn chi tiết (trừ đơn đã hủy)
        List<HoaDonChiTiet> chiTietList = hoaDonChiTietRepository.findAllWithProductDetailsExcludingCancelled();
        
        System.out.println("📦 [StatisticsService] Found " + chiTietList.size() + " invoice details (excluding cancelled)");
        
        // Map để lưu tổng số lượng mua theo nhà sản xuất
        Map<Long, BrandStatisticsDTO> brandMap = new HashMap<>();
        
        int processedCount = 0;
        int skippedCount = 0;
        
        for (HoaDonChiTiet hdct : chiTietList) {
            processedCount++;
            
            try {
                // Kiểm tra chiTietSanPham
                if (hdct.getChiTietSanPham() == null) {
                    System.out.println("⚠️ [StatisticsService] Record #" + processedCount + ": chiTietSanPham is null");
                    skippedCount++;
                    continue;
                }
                
                // Kiểm tra sanPham
                if (hdct.getChiTietSanPham().getSanPham() == null) {
                    System.out.println("⚠️ [StatisticsService] Record #" + processedCount + ": sanPham is null");
                    skippedCount++;
                    continue;
                }
                
                // Kiểm tra nhaSanXuat
                if (hdct.getChiTietSanPham().getSanPham().getNhaSanXuat() == null) {
                    System.out.println("⚠️ [StatisticsService] Record #" + processedCount + ": nhaSanXuat is null");
                    skippedCount++;
                    continue;
                }
                
                Long nhaSanXuatId = hdct.getChiTietSanPham().getSanPham().getNhaSanXuat().getId();
                String tenNhaSanXuat = hdct.getChiTietSanPham().getSanPham().getNhaSanXuat().getTenNhaSanXuat();
                Integer soLuong = hdct.getSoLuong();
                
                if (tenNhaSanXuat == null || tenNhaSanXuat.trim().isEmpty()) {
                    System.out.println("⚠️ [StatisticsService] Record #" + processedCount + ": tenNhaSanXuat is null or empty");
                    skippedCount++;
                    continue;
                }
                
                // Cập nhật hoặc tạo mới trong map
                if (brandMap.containsKey(nhaSanXuatId)) {
                    BrandStatisticsDTO existing = brandMap.get(nhaSanXuatId);
                    existing.setTongSoLuongMua(existing.getTongSoLuongMua() + soLuong);
                } else {
                    BrandStatisticsDTO brandDTO = BrandStatisticsDTO.builder()
                            .nhaSanXuatId(nhaSanXuatId)
                            .tenNhaSanXuat(tenNhaSanXuat)
                            .tongSoLuongMua(soLuong)
                            .build();
                    brandMap.put(nhaSanXuatId, brandDTO);
                }
                
            } catch (Exception e) {
                System.err.println("❌ [StatisticsService] Error processing record #" + processedCount + ": " + e.getMessage());
                skippedCount++;
            }
        }
        
        System.out.println("📊 [StatisticsService] Processing summary:");
        System.out.println("   - Total records processed: " + processedCount);
        System.out.println("   - Records skipped: " + skippedCount);
        System.out.println("   - Unique brands found: " + brandMap.size());
        
        if (brandMap.isEmpty()) {
            System.out.println("⚠️ [StatisticsService] No valid brands found!");
            return new ArrayList<>();
        }
        
        // Sắp xếp theo tổng số lượng mua giảm dần và lấy top
        List<BrandStatisticsDTO> result = brandMap.values().stream()
                .sorted((a, b) -> Integer.compare(b.getTongSoLuongMua(), a.getTongSoLuongMua()))
                .limit(limit)
                .collect(Collectors.toList());
        
        System.out.println("✅ [StatisticsService] Returning top " + result.size() + " brands:");
        for (int i = 0; i < result.size(); i++) {
            BrandStatisticsDTO dto = result.get(i);
            System.out.println("   " + (i + 1) + ". " + dto.getTenNhaSanXuat() + " | Tổng SL mua: " + dto.getTongSoLuongMua());
        }
        System.out.println("========================================");
        
        return result;
    }
    
    /**
     * Lấy thống kê số lượng đơn hàng theo từng trạng thái trong khoảng thời gian
     * @param period Loại khoảng thời gian: "day", "week", "month", "year"
     * @return Danh sách OrderStatusStatisticsDTO chứa số lượng đơn hàng theo từng trạng thái
     */
    public List<OrderStatusStatisticsDTO> getOrderStatusStatistics(String period) {
        System.out.println("========================================");
        System.out.println("📊 [StatisticsService] Getting order status statistics for: " + period);
        System.out.println("========================================");
        
        try {
            LocalDate today = LocalDate.now();
            LocalDateTime startDate;
            LocalDateTime endDate;
            
            // Tính toán khoảng thời gian theo filter
            // day: từ đầu ngày hôm nay đến hiện tại
            // month: từ đầu tháng này đến hiện tại
            // year: từ đầu năm này đến hiện tại
            switch (period.toLowerCase()) {
            case "day":
            case "today":
                // Hôm nay: từ đầu ngày hôm nay đến hiện tại
                startDate = today.atStartOfDay();
                endDate = LocalDateTime.now();
                System.out.println("📅 [StatisticsService] Filter: Day - From: " + startDate + " To: " + endDate);
                break;
            case "month":
                // Tháng này: từ ngày 1 của tháng hiện tại đến hiện tại
                startDate = LocalDate.of(today.getYear(), today.getMonth(), 1).atStartOfDay();
                endDate = LocalDateTime.now();
                System.out.println("📅 [StatisticsService] Filter: Month - From: " + startDate + " To: " + endDate);
                break;
            case "year":
                // Năm này: từ ngày 1/1 của năm hiện tại đến hiện tại
                startDate = LocalDate.of(today.getYear(), 1, 1).atStartOfDay();
                endDate = LocalDateTime.now();
                System.out.println("📅 [StatisticsService] Filter: Year - From: " + startDate + " To: " + endDate);
                break;
            case "week":
                // Tuần này: từ đầu tuần (Thứ 2) đến hiện tại
                int dayOfWeek = today.getDayOfWeek().getValue();
                startDate = today.minusDays(dayOfWeek - 1).atStartOfDay();
                endDate = LocalDateTime.now();
                System.out.println("📅 [StatisticsService] Filter: Week - From: " + startDate + " To: " + endDate);
                break;
            default:
                System.err.println("⚠️ [StatisticsService] Invalid period: " + period + ", defaulting to month");
                startDate = LocalDate.of(today.getYear(), today.getMonth(), 1).atStartOfDay();
                endDate = LocalDateTime.now();
        }
        
        System.out.println("📅 [StatisticsService] Date range: " + startDate + " to " + endDate);
        
        // Lấy tất cả đơn hàng trong khoảng thời gian
        List<HoaDon> hoaDonList = hoaDonRepository.findByNgayTaoBetween(startDate, endDate);
        System.out.println("📦 [StatisticsService] Found " + hoaDonList.size() + " invoices in period");
        
        // Đếm số lượng đơn hàng theo từng trạng thái
        Map<HoaDon.TrangThaiHoaDon, Long> statusCountMap = new HashMap<>();
        
        // Khởi tạo tất cả trạng thái với count = 0
        for (HoaDon.TrangThaiHoaDon status : HoaDon.TrangThaiHoaDon.values()) {
            statusCountMap.put(status, 0L);
        }
        
        // Đếm số lượng theo trạng thái
        for (HoaDon hoaDon : hoaDonList) {
            if (hoaDon != null && hoaDon.getTrangThai() != null) {
                HoaDon.TrangThaiHoaDon status = hoaDon.getTrangThai();
                Long currentCount = statusCountMap.get(status);
                if (currentCount != null) {
                    statusCountMap.put(status, currentCount + 1);
                } else {
                    statusCountMap.put(status, 1L);
                }
            }
        }
        
        // Tạo danh sách DTO với mapping trạng thái -> label và màu sắc
        List<OrderStatusStatisticsDTO> result = new ArrayList<>();
        
        // Map trạng thái sang label và màu (sử dụng LinkedHashMap để giữ thứ tự)
        Map<HoaDon.TrangThaiHoaDon, Map<String, String>> statusMapping = new LinkedHashMap<>();
        
        // Khởi tạo mapping cho từng trạng thái
        Map<String, String> choXacNhanMap = new HashMap<>();
        choXacNhanMap.put("label", "Chờ xác nhận");
        choXacNhanMap.put("color", "#f472b6");
        statusMapping.put(HoaDon.TrangThaiHoaDon.CHO_XAC_NHAN, choXacNhanMap);
        
        Map<String, String> choGiaoHangMap = new HashMap<>();
        choGiaoHangMap.put("label", "Chờ giao hàng");
        choGiaoHangMap.put("color", "#fbbf24");
        statusMapping.put(HoaDon.TrangThaiHoaDon.DA_XAC_NHAN, choGiaoHangMap);
        
        Map<String, String> dangGiaoMap = new HashMap<>();
        dangGiaoMap.put("label", "Đang giao");
        dangGiaoMap.put("color", "#14b8a6");
        statusMapping.put(HoaDon.TrangThaiHoaDon.DANG_GIAO_HANG, dangGiaoMap);
        
        Map<String, String> hoanThanhMap = new HashMap<>();
        hoanThanhMap.put("label", "Hoàn thành");
        hoanThanhMap.put("color", "#a855f7");
        statusMapping.put(HoaDon.TrangThaiHoaDon.DA_GIAO_HANG, hoanThanhMap);
        
        Map<String, String> daHuyMap = new HashMap<>();
        daHuyMap.put("label", "Đã hủy");
        daHuyMap.put("color", "#ef4444");
        statusMapping.put(HoaDon.TrangThaiHoaDon.DA_HUY, daHuyMap);
        
        // Tạo danh sách kết quả theo thứ tự mong muốn (luôn trả về tất cả trạng thái, kể cả count = 0)
        List<HoaDon.TrangThaiHoaDon> order = Arrays.asList(
            HoaDon.TrangThaiHoaDon.CHO_XAC_NHAN,
            HoaDon.TrangThaiHoaDon.DA_XAC_NHAN,
            HoaDon.TrangThaiHoaDon.DANG_GIAO_HANG,
            HoaDon.TrangThaiHoaDon.DA_GIAO_HANG,
            HoaDon.TrangThaiHoaDon.DA_HUY
        );
        
        for (HoaDon.TrangThaiHoaDon status : order) {
            try {
                Map<String, String> mapping = statusMapping.get(status);
                if (mapping != null) {
                    Long count = statusCountMap.get(status);
                    if (count == null) {
                        count = 0L;
                    }
                    
                    String labelValue = mapping.get("label");
                    String colorValue = mapping.get("color");
                    
                    if (labelValue == null || labelValue.isEmpty()) {
                        labelValue = status.name();
                    }
                    if (colorValue == null || colorValue.isEmpty()) {
                        colorValue = "#9ca3af"; // Màu mặc định
                    }
                    
                    OrderStatusStatisticsDTO dto = OrderStatusStatisticsDTO.builder()
                        .label(labelValue)
                        .count(count)
                        .color(colorValue)
                        .statusCode(status.name())
                        .build();
                    result.add(dto);
                    
                    System.out.println("📊 [StatisticsService] " + labelValue + ": " + count);
                }
            } catch (Exception e) {
                System.err.println("❌ [StatisticsService] Error processing status " + status + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("📊 [StatisticsService] Total statuses returned: " + result.size());
        
        System.out.println("========================================");
        
        return result;
        
        } catch (Exception e) {
            System.err.println("========================================");
            System.err.println("❌ [StatisticsService] ERROR in getOrderStatusStatistics:");
            System.err.println("   Message: " + e.getMessage());
            System.err.println("   Cause: " + (e.getCause() != null ? e.getCause().getMessage() : "N/A"));
            System.err.println("========================================");
            e.printStackTrace();
            
            // Trả về danh sách rỗng với tất cả trạng thái có count = 0 thay vì throw exception
            List<OrderStatusStatisticsDTO> errorResult = new ArrayList<>();
            errorResult.add(OrderStatusStatisticsDTO.builder()
                .label("Chờ xác nhận").count(0L).color("#f472b6").statusCode("CHO_XAC_NHAN").build());
            errorResult.add(OrderStatusStatisticsDTO.builder()
                .label("Chờ giao hàng").count(0L).color("#fbbf24").statusCode("DA_XAC_NHAN").build());
            errorResult.add(OrderStatusStatisticsDTO.builder()
                .label("Đang giao").count(0L).color("#14b8a6").statusCode("DANG_GIAO_HANG").build());
            errorResult.add(OrderStatusStatisticsDTO.builder()
                .label("Hoàn thành").count(0L).color("#a855f7").statusCode("DA_GIAO_HANG").build());
            errorResult.add(OrderStatusStatisticsDTO.builder()
                .label("Đã hủy").count(0L).color("#ef4444").statusCode("DA_HUY").build());
            
            return errorResult;
        }
    }
    
    /**
     * Lấy thống kê phân phối đa kênh
     * Logic: 
     * - Online: Hóa đơn không có nhân viên (nhan_vien_id IS NULL)
     * - Tại quầy: Hóa đơn có nhân viên (nhan_vien_id IS NOT NULL)
     * @return Danh sách ChannelStatisticsDTO
     */
    public List<ChannelStatisticsDTO> getChannelStatistics() {
        System.out.println("========================================");
        System.out.println("📊 [StatisticsService] Getting channel statistics");
        System.out.println("========================================");
        
        try {
            // Đếm số lượng đơn hàng Online
            // Logic: Hóa đơn không có nhan_vien_id hoặc nhan_vien_id IS NULL
            Long onlineCount = hoaDonRepository.countByNhanVienIsNull();
            
            // Đếm số lượng đơn hàng Tại quầy
            // Logic: Hóa đơn có mã nhân viên (nhan_vien_id IS NOT NULL)
            Long taiQuayCount = hoaDonRepository.countByNhanVienIsNotNull();
            
            System.out.println("📊 [StatisticsService] Channel Statistics:");
            System.out.println("   - Online (nhan_vien_id IS NULL): " + onlineCount);
            System.out.println("   - Tại quầy (nhan_vien_id IS NOT NULL): " + taiQuayCount);
            
            List<ChannelStatisticsDTO> result = new ArrayList<>();
            
            // Thêm kênh Online
            result.add(ChannelStatisticsDTO.builder()
                .channel("Online")
                .count(onlineCount != null ? onlineCount : 0L)
                .color("#f472b6") // Pink
                .build());
            
            // Thêm kênh Tại quầy
            result.add(ChannelStatisticsDTO.builder()
                .channel("Tại quầy")
                .count(taiQuayCount != null ? taiQuayCount : 0L)
                .color("#3b82f6") // Blue
                .build());
            
            System.out.println("✅ [StatisticsService] Returning " + result.size() + " channels");
            System.out.println("========================================");
            
            return result;
        } catch (Exception e) {
            System.err.println("========================================");
            System.err.println("❌ [StatisticsService] ERROR in getChannelStatistics:");
            System.err.println("   Message: " + e.getMessage());
            System.err.println("   Cause: " + (e.getCause() != null ? e.getCause().getMessage() : "N/A"));
            System.err.println("========================================");
            e.printStackTrace();
            
            // Trả về danh sách rỗng với count = 0 khi có lỗi
            List<ChannelStatisticsDTO> errorResult = new ArrayList<>();
            errorResult.add(ChannelStatisticsDTO.builder()
                .channel("Online").count(0L).color("#f472b6").build());
            errorResult.add(ChannelStatisticsDTO.builder()
                .channel("Tại quầy").count(0L).color("#3b82f6").build());
            
            return errorResult;
        }
    }
    
    /**
     * Lấy danh sách sản phẩm sắp hết hàng dựa trên số lượng tồn kho
     * Logic:
     * - Lấy các sản phẩm có soLuongTon <= threshold
     * - Sắp xếp theo số lượng tăng dần (sản phẩm ít nhất lên đầu)
     * - Chỉ lấy sản phẩm có trangThai = true (đang hoạt động)
     * @param threshold Ngưỡng số lượng (mặc định 5)
     * @param limit Số lượng sản phẩm tối đa muốn lấy (mặc định 10)
     * @return Danh sách LowStockProductDTO
     */
    public List<LowStockProductDTO> getLowStockProducts(Integer threshold, Integer limit) {
        System.out.println("========================================");
        System.out.println("📊 [StatisticsService] Getting low stock products");
        System.out.println("   Threshold: " + threshold + ", Limit: " + limit);
        System.out.println("========================================");
        
        try {
            // Mặc định threshold = 5 nếu không có
            final int finalThreshold = (threshold == null || threshold < 0) ? 5 : threshold;
            
            // Mặc định limit = 10 nếu không có
            final int finalLimit = (limit == null || limit <= 0) ? 10 : limit;
            
            // Lấy tất cả sản phẩm có số lượng tồn <= threshold và đang hoạt động
            List<SanPham> sanPhamList = sanPhamRepository.findAll().stream()
                    .filter(sp -> sp.getTrangThai() != null && sp.getTrangThai()) // Chỉ lấy sản phẩm đang hoạt động
                    .filter(sp -> sp.getSoLuongTon() != null && sp.getSoLuongTon() <= finalThreshold)
                    .sorted((a, b) -> {
                        // Sắp xếp theo số lượng tăng dần (ít nhất lên đầu)
                        int qtyA = a.getSoLuongTon() != null ? a.getSoLuongTon() : Integer.MAX_VALUE;
                        int qtyB = b.getSoLuongTon() != null ? b.getSoLuongTon() : Integer.MAX_VALUE;
                        return Integer.compare(qtyA, qtyB);
                    })
                    .limit(finalLimit)
                    .collect(Collectors.toList());
            
            System.out.println("📦 [StatisticsService] Found " + sanPhamList.size() + " low stock products");
            
            // Chuyển đổi sang DTO
            List<LowStockProductDTO> result = sanPhamList.stream()
                    .map(sp -> LowStockProductDTO.builder()
                            .sanPhamId(sp.getId())
                            .tenSanPham(sp.getTenSanPham())
                            .soLuongTon(sp.getSoLuongTon() != null ? sp.getSoLuongTon() : 0)
                            .build())
                    .collect(Collectors.toList());
            
            System.out.println("✅ [StatisticsService] Returning " + result.size() + " low stock products:");
            for (int i = 0; i < result.size(); i++) {
                LowStockProductDTO dto = result.get(i);
                System.out.println("   " + (i + 1) + ". " + dto.getTenSanPham() + " | Số lượng: " + dto.getSoLuongTon());
            }
            System.out.println("========================================");
            
            return result;
        } catch (Exception e) {
            System.err.println("========================================");
            System.err.println("❌ [StatisticsService] ERROR in getLowStockProducts:");
            System.err.println("   Message: " + e.getMessage());
            System.err.println("   Cause: " + (e.getCause() != null ? e.getCause().getMessage() : "N/A"));
            System.err.println("========================================");
            e.printStackTrace();
            
            // Trả về danh sách rỗng khi có lỗi
            return new ArrayList<>();
        }
    }
}


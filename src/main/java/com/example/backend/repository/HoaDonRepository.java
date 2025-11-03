package com.example.backend.repository;

import com.example.backend.entity.HoaDon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface HoaDonRepository extends JpaRepository<HoaDon, Long>, JpaSpecificationExecutor<HoaDon> {
    
    Optional<HoaDon> findByMaHoaDon(String maHoaDon);
    
    List<HoaDon> findByTrangThai(HoaDon.TrangThaiHoaDon trangThai);
    
    List<HoaDon> findByKhachHangId(Long khachHangId);
    
    List<HoaDon> findByNhanVienId(Long nhanVienId);
    
    @Query("SELECT h FROM HoaDon h WHERE h.ngayTao BETWEEN :startDate AND :endDate")
    List<HoaDon> findByNgayTaoBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("SELECT h FROM HoaDon h WHERE h.ngayTao BETWEEN :startDate AND :endDate " +
           "AND h.trangThai != com.example.backend.entity.HoaDon$TrangThaiHoaDon.DA_HUY ORDER BY h.ngayTao DESC")
    List<HoaDon> findByNgayTaoBetweenExcludingCancelled(LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("SELECT h FROM HoaDon h ORDER BY h.ngayTao DESC")
    List<HoaDon> findAllOrderByNgayTaoDesc();
    
    // Đếm số lượng hóa đơn không có nhân viên (Online)
    // Logic: nhan_vien_id IS NULL hoặc không có nhân viên
    @Query("SELECT COUNT(h) FROM HoaDon h WHERE h.nhanVien IS NULL")
    Long countByNhanVienIsNull();
    
    // Đếm số lượng hóa đơn có nhân viên (Tại quầy)
    // Logic: nhan_vien_id IS NOT NULL (có mã nhân viên)
    @Query("SELECT COUNT(h) FROM HoaDon h WHERE h.nhanVien IS NOT NULL")
    Long countByNhanVienIsNotNull();
}

package com.sprint.findex_team6.service;

import static com.sprint.findex_team6.error.ErrorCode.INDEX_NOT_FOUND;

import com.opencsv.CSVWriter;
import com.sprint.findex_team6.dto.CursorPageResponse;
import com.sprint.findex_team6.dto.IndexDataDto;
import com.sprint.findex_team6.dto.dashboard.ChartDataPoint;
import com.sprint.findex_team6.dto.dashboard.IndexChartDto;
import com.sprint.findex_team6.dto.dashboard.IndexPerformanceDto;
import com.sprint.findex_team6.dto.dashboard.RankedIndexPerformanceDto;
import com.sprint.findex_team6.dto.request.IndexDataCreateRequest;
import com.sprint.findex_team6.dto.request.IndexDataQueryRequest;
import com.sprint.findex_team6.dto.request.IndexDataUpdateRequest;
import com.sprint.findex_team6.entity.Index;
import com.sprint.findex_team6.entity.IndexVal;
import com.sprint.findex_team6.entity.SourceType;
import com.sprint.findex_team6.error.CustomException;
import com.sprint.findex_team6.exception.NotFoundException;
import com.sprint.findex_team6.mapper.CursorPageResponseMapper;
import com.sprint.findex_team6.mapper.IndexValMapper;
import com.sprint.findex_team6.repository.IndexRepository;
import com.sprint.findex_team6.repository.IndexValRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IndexValService {

  private final IndexValRepository indexValRepository;
  private final IndexRepository indexRepository;
  private final IndexValMapper indexValMapper;
  private final CursorPageResponseMapper cursorPageResponseMapper;

  @Transactional
  public IndexDataDto update (Long id, IndexDataUpdateRequest indexDataUpdateRequest){
    IndexVal indexVal = indexValRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("IndexVal not found. id=" + id));

    if (indexDataUpdateRequest.marketPrice() != null) {
      indexVal.setMarketPrice(indexDataUpdateRequest.marketPrice());
    }
    if (indexDataUpdateRequest.closingPrice() != null) {
      indexVal.setClosingPrice(indexDataUpdateRequest.closingPrice());
    }
    if (indexDataUpdateRequest.highPrice() != null) {
      indexVal.setHighPrice(indexDataUpdateRequest.highPrice());
    }
    if (indexDataUpdateRequest.lowPrice() != null) {
      indexVal.setLowPrice(indexDataUpdateRequest.lowPrice());
    }
    if (indexDataUpdateRequest.versus() != null) {
      indexVal.setVersus(indexDataUpdateRequest.versus());
    }
    if (indexDataUpdateRequest.fluctuationRate() != null) {
      indexVal.setFluctuationRate(indexDataUpdateRequest.fluctuationRate());
    }
    if (indexDataUpdateRequest.tradingQuantity() != null) {
      indexVal.setTradingQuantity(indexDataUpdateRequest.tradingQuantity());
    }
    if (indexDataUpdateRequest.tradingPrice() != null) {
      indexVal.setTradingPrice(BigDecimal.valueOf(indexDataUpdateRequest.tradingPrice()));
    }
    if (indexDataUpdateRequest.marketTotalAmount() != null) {
      indexVal.setMarketTotalAmount(BigDecimal.valueOf(indexDataUpdateRequest.marketTotalAmount()));
    }
    return indexValMapper.toDto(indexVal);
  }

  /**
   * 정렬 필드 및 방향 설정
   */

  private Sort getSort(String sortField, String sortDirection) {
    if (sortField == null || sortField.isBlank()) {
      sortField = "baseDate";
    }
    Sort.Direction direction =
        "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
    return Sort.by(direction, sortField);
  }
  @Transactional
    public IndexDataDto create (IndexDataCreateRequest request){
      Index index = indexRepository.findById(request.indexInfoId())
          .orElseThrow(() -> new NotFoundException("Index not found. id=" + request.indexInfoId()));
      IndexVal indexVal = IndexVal.builder()
          .baseDate(request.baseDate())
          .sourceType(SourceType.USER)
          .marketPrice(request.marketPrice())
          .closingPrice(request.closingPrice())
          .highPrice(request.highPrice())
          .lowPrice(request.lowPrice())
          .versus(request.versus())
          .fluctuationRate(request.fluctuationRate())
          .tradingQuantity(request.tradingQuantity())
          .tradingPrice(BigDecimal.valueOf(request.tradingPrice()))
          .marketTotalAmount(BigDecimal.valueOf(request.marketTotalAmount()))
          .index(index)
          .build();

      indexVal = indexValRepository.save(indexVal);
      return indexValMapper.toDto(indexVal);
    }

    public CursorPageResponse<IndexDataDto> findIndexData (IndexDataQueryRequest request, Pageable
    pageable){
      //값 세팅
      Long indexInfoId = request.indexInfoId();
      LocalDate startDate = request.startDate() == null ?
          LocalDate.of(1900, 1, 1) : request.startDate();
      LocalDate endDate = request.endDate() == null ?
          LocalDate.now() : request.endDate();
      String cursor = request.cursor();
      Long idAfter = request.idAfter();

      //indexInfoId가 null이면 indexInfoId에 관계 없이 최신 데이터 리턴
      if (indexInfoId == null) {
        Page<IndexDataDto> page = indexValRepository.findAll(pageable)
            .map(indexValMapper::toDto);
        return cursorPageResponseMapper.fromPageIndexDataDto(page);
      }

      Sort sort = pageable.getSort();
      Order order = sort.iterator().next();
      String property = order.getProperty();
      Page<IndexVal> page;

      if (cursor == null) { //cursor가 null 이면 startDate-endDate 사이의 데이터 리턴
        page = indexValRepository.findByIndex_IdAndBaseDateBetween(
            indexInfoId, startDate, endDate, pageable);
      } else if (property.equals("closingPrice")) { //정렬 필드가 closingPrice인 경우
        if (order.getDirection().isDescending()) { //내림차순이면 cursor보다 작은 데이터를 찾음
          page = indexValRepository.findByClosingPriceCursorDesc(
              indexInfoId, startDate, endDate, new BigDecimal(cursor), idAfter, pageable);
        } else { //오름차순이면 cursor보다 큰 데이터를 찾음
          page = indexValRepository.findByClosingPriceCursorAsc(
              indexInfoId, startDate, endDate, new BigDecimal(cursor), idAfter, pageable);
        }
      } else { //정렬 필드가 baseDate인 경우
        if (order.getDirection().isDescending()) { //위와 같이 내림차순, 오름차순 처리
          page = indexValRepository.findByBaseDateCursorDesc(
              indexInfoId, startDate, endDate, LocalDate.parse(cursor), idAfter, pageable);
        } else {
          page = indexValRepository.findByBaseDateCursorAsc(
              indexInfoId, startDate, endDate, LocalDate.parse(cursor), idAfter, pageable);
        }
      }
      return cursorPageResponseMapper.fromPageIndexDataDto(page.map(indexValMapper::toDto));
    }

  @Transactional
  public void delete (Long id){
    indexValRepository.deleteById(id);
  }

  //관심지수 성과 조회
  @Transactional(readOnly = true)
  public List<IndexPerformanceDto> getInterestIndexPerformance(String periodType) {
    List<Index> favoriteIndexes = indexRepository.findByFavorite(true);

    //조회할 시작, 종료 날짜 설정
    LocalDate startDate = calculateStartDate(periodType);
    LocalDate endDate = LocalDate.now();

    //즐겨찾기 한 지수 ID목록 추출
    List<Long> indexIds = favoriteIndexes.stream().map(Index::getId).toList();

    List<IndexVal> indexValList = indexValRepository.findByIndex_IdInAndBaseDateBetween(
        indexIds, startDate, endDate);

    Map<Long, LocalDate> earliestDateByIndex = new HashMap<>();
    Map<Long, LocalDate> latestDateByIndex = new HashMap<>();

    for (IndexVal val : indexValList) {
      Long indexId = val.getIndex().getId();
      LocalDate date = val.getBaseDate();

      if (!earliestDateByIndex.containsKey(indexId) ||
          date.isBefore(earliestDateByIndex.get(indexId))) {
        earliestDateByIndex.put(indexId, date);
      }

      if (!latestDateByIndex.containsKey(indexId) ||
          date.isAfter(latestDateByIndex.get(indexId))) {
        latestDateByIndex.put(indexId, date);
      }
    }

    Map<Long, IndexVal> startDataMap = indexValList.stream()
        .filter(data -> earliestDateByIndex.containsKey(data.getIndex().getId()) &&
            data.getBaseDate().equals(earliestDateByIndex.get(data.getIndex().getId())))
        .collect(Collectors.toMap(data -> data.getIndex().getId(), Function.identity()));

    Map<Long, IndexVal> endDataMap = indexValList.stream()
        .filter(data -> latestDateByIndex.containsKey(data.getIndex().getId()) &&
            data.getBaseDate().equals(latestDateByIndex.get(data.getIndex().getId())))
        .collect(Collectors.toMap(data -> data.getIndex().getId(), Function.identity()));

    return favoriteIndexes.stream()
        .map(index -> createIndexPerformanceDto(index, startDataMap, endDataMap))
        .flatMap(Optional::stream)
        .collect(Collectors.toList());
  }

  // 기간 타입에 따른 시작 날짜 계산
  private LocalDate calculateStartDate(String periodType) {
    LocalDate endDate = LocalDate.now();
    return switch (periodType) {
      case "DAILY" -> endDate.minusDays(1);
      case "WEEKLY" -> endDate.minusWeeks(1);
      case "MONTHLY" -> endDate.minusMonths(1);
      case "QUARTERLY" -> endDate.minusMonths(3);
      case "YEARLY" -> endDate.minusYears(1);
      default -> throw new IllegalStateException("Unexpected value: " + periodType);
    };
  }

  //지수의 성과 데이터 생성
  private Optional<IndexPerformanceDto> createIndexPerformanceDto(
      Index index, Map<Long, IndexVal> startDataMap, Map<Long, IndexVal> endDataMap) {
    IndexVal startData = startDataMap.get(index.getId());
    IndexVal endData = endDataMap.get(index.getId());

    if (startData != null && endData != null) {
      BigDecimal startPrice = startData.getClosingPrice();
      BigDecimal endPrice = endData.getClosingPrice();
      BigDecimal versus = endPrice.subtract(startPrice);
      BigDecimal fluctuationRate = versus.divide(startPrice, 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100));

      return Optional.of(new IndexPerformanceDto(
          index.getId().longValue(),
          index.getIndexClassification(),
          index.getIndexName(),
          versus,
          fluctuationRate,
          endPrice,
          startPrice
      ));
    }
    return Optional.empty();
  }

  //특정 지수 성과 순위 조회
  public List<RankedIndexPerformanceDto> getIndexPerformanceRank(String periodType, Long indexInfoId, int limit) {
    LocalDate beforeDate = calculateStartDate(periodType);
    LocalDate today = LocalDate.now();
    List<IndexPerformanceDto> result = new ArrayList<>(100);

    //동일 분류 지수 가져오기
    List<Index> indexInfoList = indexRepository.findAll();
    for (Index index : indexInfoList) {
      Optional<IndexVal> start = indexValRepository
          .findFirstByIndexAndBaseDateGreaterThanOrderByBaseDateAsc(index, beforeDate);
      Optional<IndexVal> end = indexValRepository
          .findFirstByIndexAndBaseDateLessThanOrderByBaseDateDesc(index, today);
      if (start.isEmpty() || end.isEmpty()) {
        continue;
      }

      IndexPerformanceDto indexPerformanceDto = createIndexPerformanceDto(
          index, start.get(), end.get());
      result.add(indexPerformanceDto);
    }

    result.sort(Comparator.comparing(IndexPerformanceDto::fluctuationRate).reversed());
    List<RankedIndexPerformanceDto> dtos = new ArrayList<>(result.size());
    int rank = 0;
    for (IndexPerformanceDto dto : result) {
      dtos.add(new RankedIndexPerformanceDto(dto, ++rank));
    }

    if (indexInfoId != null) {
      return dtos.stream()
          .filter(dto -> dto.performance().indexInfoId().equals(indexInfoId))
          .toList();
    } else {
      return dtos.size() < limit ? dtos : dtos.subList(0, limit);
    }
  }

  private IndexPerformanceDto createIndexPerformanceDto(Index index, IndexVal start, IndexVal end) {

    BigDecimal startPrice = start.getClosingPrice();
    BigDecimal endPrice = end.getClosingPrice();
    BigDecimal versus = endPrice.subtract(startPrice);
    BigDecimal fluctuationRate = versus.divide(startPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

    return new IndexPerformanceDto(
        index.getId(),
        index.getIndexClassification(),
        index.getIndexName(),
        versus,
        fluctuationRate,
        startPrice,
        endPrice
    );
  }

  //특정 지수 차트 데이터 조회
  public IndexChartDto getIndexChart(String periodType, Long indexId) {
    LocalDate startDate = calculateStartDate(periodType);
    LocalDate endDate = LocalDate.now();

    Index indexInfo = indexRepository.findById(indexId)
        .orElseThrow(() -> new CustomException(INDEX_NOT_FOUND));

    List<IndexVal> indexDataList = indexValRepository
        .findByIndexAndBaseDateBetweenOrderByBaseDateAsc(indexInfo, startDate, endDate);

    List<ChartDataPoint> dataPoints = indexDataList.stream()
        .map(data -> new ChartDataPoint(data.getBaseDate(), data.getClosingPrice()))
        .toList();

    List<ChartDataPoint> ma5DataPoints = calculateMovingAverage(dataPoints, 5);
    List<ChartDataPoint> ma20DataPoints = calculateMovingAverage(dataPoints, 20);

    return new IndexChartDto(
        indexId,
        indexInfo.getIndexClassification(),
        indexInfo.getIndexName(),
        periodType,
        dataPoints,
        ma5DataPoints,
        ma20DataPoints
    );
  }

  //지수 차트 전체 조회
  @Transactional(readOnly = true)
  public List<IndexChartDto> getIndexCharts(String periodType, List<Long> indexIds) {
    LocalDate startDate = calculateStartDate(periodType);
    LocalDate endDate = LocalDate.now();

    List<IndexChartDto> indexCharts = new ArrayList<>();
    for (Long indexId : indexIds) {
      Index index = indexRepository.findById(indexId)
          .orElseThrow(() -> new CustomException(INDEX_NOT_FOUND));
      List<IndexVal> indexValList = indexValRepository.findByIndexAndBaseDateBetweenOrderByBaseDateAsc(index, startDate, endDate);
      List<ChartDataPoint> dataPoints = indexValList.stream()
          .map(data -> new ChartDataPoint(data.getBaseDate(), data.getClosingPrice()))
          .collect(Collectors.toList());

      List<ChartDataPoint> ma5DataPoints = calculateMovingAverage(dataPoints, 5);
      List<ChartDataPoint> ma20DataPoints = calculateMovingAverage(dataPoints, 20);

      indexCharts.add(new IndexChartDto(
          indexId,
          index.getIndexClassification(),
          index.getIndexName(),
          periodType,
          dataPoints,
          ma5DataPoints,
          ma20DataPoints
      ));
    }
    return indexCharts;
  }

  private List<ChartDataPoint> calculateMovingAverage(List<ChartDataPoint> dataPoints, int period) {
    List<ChartDataPoint> maDataPoints = new ArrayList<>();
    for (int i = period - 1; i < dataPoints.size(); i++) {
      BigDecimal sum = BigDecimal.ZERO;
      for (int j = 0; j < period; j++) {
        sum = sum.add(dataPoints.get(i - j).value());
      }
      BigDecimal average = sum.divide(BigDecimal.valueOf(period), 4, BigDecimal.ROUND_HALF_UP);
      maDataPoints.add(new ChartDataPoint(dataPoints.get(i).baseDate(), average));
    }
    return maDataPoints;
  }

  /**
   * CSV 파일 생성 및 응답
   */
  public void exportIndexDataToCsv(Long indexInfoId, String startDateStr, String endDateStr,
      String sortField, String sortDirection, HttpServletResponse response) {

    // 날짜 파싱 (기본값: startDate = 1년 전, endDate = 오늘)
    LocalDate startDate = parseDateOrDefault(startDateStr, LocalDate.now().minusYears(1));
    LocalDate endDate = parseDateOrDefault(endDateStr, LocalDate.now());

    // 정렬 필드와 방향 설정
    Sort sort = getSort(sortField, sortDirection);

    // 데이터 조회
    List<IndexVal> indexData = indexValRepository.findByIndexIdAndBaseDateBetween(indexInfoId,startDate, endDate, sort);

    // CSV 파일 응답 설정
    response.setContentType("text/csv");
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Content-Disposition", "attachment; filename=\"index_data-export.csv\"");

    try (PrintWriter writer = response.getWriter();
        CSVWriter csvWriter = new CSVWriter(writer)) {

      // CSV 헤더 작성
      String[] header = {"기준일자", "종가", "고가", "저가", "전일대비등락", "등락률", "거래량", "거래대금", "시가총액"};
      csvWriter.writeNext(header);

      // 데이터 추가
      for (IndexVal data : indexData) {
        String[] row = {
            data.getBaseDate().toString(),
            data.getClosingPrice().toString(),
            data.getHighPrice().toString(),
            data.getLowPrice().toString(),
            data.getVersus().toString(),
            data.getFluctuationRate().toString(),
            data.getTradingQuantity().toString(),
            data.getTradingPrice().toString(),
            data.getMarketTotalAmount().toString()
        };
        csvWriter.writeNext(row);
      }

    } catch (Exception e) {
      log.error("CSV 파일 생성 중 오류 발생", e);
      throw new RuntimeException("CSV 파일을 생성하는 중 오류가 발생했습니다.", e);
    }
  }

  /**
   * 날짜 파싱 메서드 (유효하지 않으면 기본값 사용)
   */
  private LocalDate parseDateOrDefault(String dateStr, LocalDate defaultDate) {
   if(dateStr == null || dateStr.isEmpty()) {
     return defaultDate;
   }
   return LocalDate.parse(dateStr);
  }
}

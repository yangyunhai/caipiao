package com.lotterysource.portsadmin.service;
import com.aliyun.oss.common.utils.DateUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.lotterysource.portsadmin.dbprovider.PortsAdminRepository;
import com.lotterysource.portsadmin.entity.*;
import com.lotterysource.portsadmin.messaging.ApiResult;
import com.lotterysource.portsadmin.utils.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Administrator on 2024/2/1.
 */
@Service
public class PortsAdminService {

    @Autowired
    PortsAdminRepository portsAdminRepository;

    private static Logger logger = LoggerFactory.getLogger(PortsAdminService.class);

    @Autowired
    RedisStringUtil redisStringUtil;

    @Value("${key}")
    private String key;
    private Long userId;
    private String fileUrl;

    public void deleteLottery(Long id,Long userId) {
        LotteryData lotteryData = portsAdminRepository.findLotteryDataBy(id);
        if(lotteryData!=null){
            if(!Objects.equals(lotteryData.getUserId(),userId)){
                throw new ServiceInvokeException(-1, "该条数据是参与报名数据，不是本人没有删除权限");
            }

            List ids = new ArrayList();
            ids.add(lotteryData.getId());
            List<LotteryOrder> lotteryOrderList = portsAdminRepository.findLotteryOrderList(ids);
            if(lotteryOrderList!=null && lotteryOrderList.size()>0){
                throw new ServiceInvokeException(-1, "该条数据有人参与报名，不能删除");
            }
            portsAdminRepository.deleteLottery(id);
            portsAdminRepository.deleteLotteryNumber(id);
        }
    }

    /**
     * 计算方法(彩票注数)
     *
     * @param n    总球数量
     * @param k    选择多少球组合
     * @param l    蓝球
     * @param d    胆
     * @param t    拖
     * @param x    快乐选几（1-10）
     * @param type 类型
     * @return
     */
    public Integer getAlgorithm(Integer n,
                                Integer k,
                                Integer l,
                                Integer d,
                                Integer t,
                                Integer x,
                                Integer g,
                                Integer s,
                                Integer b,
                                String type) {

        int i = 0;
        if (type.equals("qlcFs")) {
            //七彩乐复式
            i = comBin(n, 7);
        } else if (type.equals("qlcDt")) {
            //七彩乐胆拖
            i = comBin(t, 7 - d);
        } else if (type.equals("ssqFs")) {
            //双色球复式
            i = comBin(n, 6) * l;
        } else if (type.equals("ssqDt")) {
            //双色球胆拖
            i = comBin(t, 6 - d) * comBin(l, 1);
        } else if (type.equals("kl8Fs")) {
            //快乐8复式
            i = comBin(n, x);
        } else if (type.equals("kl8Dt")) {
            //快乐8胆拖
            i = comBin(t, x - d);
        } else if (type.equals("fc3d")) {

        } else if (type.equals("fc3dZx")) {
            //自选
            i = comBin(g, 1) * comBin(s, 1) * comBin(b, 1);
        } else if (type.equals("fc3dZ3")) {
            int count = 0; // 计数器
            for (int i1 = 0; i1 < n; i1++) {
                for (int j = 0; j < n; j++) {
                    if (i1 != j) { // 确保两个数字不同
                        count++;
                    }
                }
            }
            i = count;
        } else if (type.equals("fc3dZ6")) {
            i = comBin(n, 3);
        } else if (type.equals("fc3dJx")) {
            i = n;
        }

        return i;
    }


    /**
     * n（总个数）和k（选择几个数作为排列组合）
     *
     * @param n
     * @param k
     * @return
     */
    public static int comBin(int n, int k) {
        List<List<Integer>> result = new ArrayList<>();
        combineHelper(n, k, 1, new ArrayList<>(), result);
        return result.size();
    }

    private static void combineHelper(int n, int k, int start, List<Integer> combinationSoFar, List<List<Integer>> result) {
        if (k == 0) {
            result.add(new ArrayList<>(combinationSoFar));
            return;
        }
        for (int i = start; i <= n; i++) {
            combinationSoFar.add(i);
            combineHelper(n, k - 1, i + 1, combinationSoFar, result);
            combinationSoFar.remove(combinationSoFar.size() - 1);
        }
    }

    public static int fc3dZu3(List<Integer> numbers) {
        int count = 0; // 计数器
        for (int i = 0; i < numbers.size(); i++) {
            for (int j = 0; j < numbers.size(); j++) {
                if (i != j) { // 确保两个数字不同
                    //System.out.println(numbers[i] * 100 + numbers[j] * 10 + numbers[i]);
                    System.out.println(String.valueOf(numbers.get(i))
                            + String.valueOf(numbers.get(j))
                            + String.valueOf(numbers.get(i)));

                    count++;
                }
            }
        }
        System.out.println("Total number of 3-digit numbers: " + count);
        return count;
    }

    public void saveLottery(Long userId, String type, String typeTwo, Integer typeKl, String ballDsStr
            , List<String> numberRedStr, List<String> numberBlueStr, List<String> numberDanStr
            , List<String> numberTuoStr, String number3dZx, int number) {
        //彩票注数
        int zs = getZs(type, typeTwo, typeKl, ballDsStr, numberRedStr, numberBlueStr, numberDanStr, numberTuoStr, number3dZx);
        List<LotteryBall> lotteryBallList = new ArrayList<>();
        if (ballDsStr != null && ballDsStr.length() > 0) {
            lotteryBallList = JsonUtil.deserialize(ballDsStr
                    , new TypeReference<List<LotteryBall>>() {
                    });
        }
        double money = zs * 2d;
        if ((money * number) > 20000) {
            throw new ServiceInvokeException("单张限额2万");
        }
        LotteryData lotteryData = new LotteryData();
        Date now = new Date();
        if (Objects.equals(type, "ssq")) {
            Date ssqOpenDate = DateUtils.getSsqOpenDate(now);
            lotteryData.setOpenDate(ssqOpenDate);
        } else if (Objects.equals(type, "qlc")) {
            Date qlcOpenDate = DateUtils.getQlcOpenDate(now);
            lotteryData.setOpenDate(qlcOpenDate);
        } else if (Objects.equals(type, "kl8")) {
            Date stopTime = new Date();
            stopTime.setHours(21);
            stopTime.setMinutes(30);
            stopTime.setSeconds(0);
            if (now.getTime() > stopTime.getTime()) {
                Date date = DateUtils.addDays(now, 1);
                lotteryData.setOpenDate(date);
            }
        } else if (Objects.equals(type, "fc3d")) {
            Date stopTime = new Date();
            stopTime.setHours(21);
            stopTime.setMinutes(15);
            stopTime.setSeconds(0);
            if (now.getTime() > stopTime.getTime()) {
                Date date = DateUtils.addDays(now, 1);
                lotteryData.setOpenDate(date);
            }
        }
        lotteryData.setMoney(money);
        lotteryData.setType(type);
        lotteryData.setTypeTwo(typeTwo);
        lotteryData.setTypeKl(typeKl);
        lotteryData.setUserId(userId);
        lotteryData.setNumber(number);
        portsAdminRepository.saveLotteryData(lotteryData);
        saveLotteryNumber(lotteryBallList, lotteryData, numberRedStr, numberBlueStr, numberDanStr, numberTuoStr
                , number3dZx);
    }

    public String getNumberString(List<String> numberStr) {
        String str = null;
        if (numberStr != null && numberStr.size() > 0) {
            str = numberStr.stream().collect(Collectors.joining(","));
        }
        return str;
    }

    public int getZs(String type, String typeTwo, Integer typeKl, String ballDsStr
            , List<String> numberRedStr, List<String> numberBlueStr
            , List<String> numberDanStr, List<String> numberTuoStr, String number3dZx) {
        //彩票注数
        int zs = 0;
        if (type.equals("fc3d")) {
            if (typeTwo.equals("Zx")) {
                //自选
                Fc3dZx fc3dZx = JsonUtil.deserialize(number3dZx, new TypeReference<Fc3dZx>() {
                });
                List<String> g = Arrays.asList(fc3dZx.getG().split(","));
                List<String> s = Arrays.asList(fc3dZx.getG().split(","));
                List<String> b = Arrays.asList(fc3dZx.getG().split(","));
                zs = comBin(g.size(), 1) * comBin(s.size(), 1) * comBin(b.size(), 1);
            } else if (typeTwo.equals("Z3")) {
                List<Integer> red = new ArrayList<>();
                for (String s : numberRedStr) {
                    red.add(NumberUtil.parseInteger(s));
                }
                zs = fc3dZ3(red);
            } else if (typeTwo.equals("Z6")) {
                zs = comBin(numberRedStr.size(), 3);
            } else if (typeTwo.equals("Jx")) {
                //zs = 1;
                List<LotteryBall> lotteryBallList = JsonUtil.deserialize(ballDsStr
                        , new TypeReference<List<LotteryBall>>() {
                        });
                zs = lotteryBallList.size();
            }
        } else {
            zs = getZs(type, typeTwo, typeKl, ballDsStr, numberRedStr, numberBlueStr, numberDanStr, numberTuoStr);
        }
        return zs;
    }

    public Integer getZs(String type, String typeTwo, Integer typeKl, String ballDsStr
            , List<String> numberRedStr, List<String> numberBlueStr
            , List<String> numberDanStr, List<String> numberTuoStr) {
        int zs = 0;
        if ("Ds".equals(typeTwo)) {
            //单式
            if (ballDsStr == null || ballDsStr.length() == 0) {
                throw new ServiceInvokeException(-1, "单式玩法参数错误");
            }
            List<LotteryBall> lotteryBallList = JsonUtil.deserialize(ballDsStr
                    , new TypeReference<List<LotteryBall>>() {
                    });
            zs = lotteryBallList.size();
        } else {
            Integer n = null;
            if (numberRedStr != null && numberRedStr.size() > 0) {
                n = numberRedStr.size();
            }
            Integer k = null;
            Integer l = null;
            if (numberBlueStr != null && numberBlueStr.size() > 0) {
                l = numberBlueStr.size();
            }
            Integer d = null;
            if (numberDanStr != null && numberDanStr.size() > 0) {
                d = numberDanStr.size();
            }
            Integer t = null;
            if (numberTuoStr != null && numberTuoStr.size() > 0) {
                t = numberTuoStr.size();
            }
            Integer x = null;
            if (typeKl != null) {
                x = typeKl;
            }
            String typeNew = type + typeTwo;
            zs = getAlgorithm(n, k, l, d, t, x, null, null, null, typeNew);
        }
        if (zs == 0) {
            throw new ServiceInvokeException("数据有误");
        }
        return zs;
    }


    public int fc3dZ3(List<Integer> numbers) {
        int count = 0; // 计数器
        for (int i = 0; i < numbers.size(); i++) {
            for (int j = 0; j < numbers.size(); j++) {
                if (i != j) { // 确保两个数字不同
//                    System.out.println(String.valueOf(numbers.get(i))
//                            + String.valueOf(numbers.get(j))
//                            + String.valueOf(numbers.get(i)));
                    count++;
                }
            }
        }
        System.out.println("Total number of 3-digit numbers: " + count);
        return count;
    }

    public void updateLottery(Long lotteryDataId, String type, String typeTwo, Integer typeKl, String ballDsStr
            , List<String> numberRedStr, List<String> numberBlueStr, List<String> numberDanStr
            , List<String> numberTuoStr, String number3dZx, int number) {

        LotteryData l = portsAdminRepository.findLotteryDataBy(lotteryDataId);
        //if (l != null && l.getQrCode() != null) {
        //    throw new ServiceInvokeException(-1, "该数据已生成分享二维码，暂不支持修改");
        //}

        List ids = new ArrayList();
        ids.add(l.getId());
        List<LotteryOrder> lotteryOrderList = portsAdminRepository.findLotteryOrderList(ids);
        if(lotteryOrderList!=null && lotteryOrderList.size()>0){
            throw new ServiceInvokeException(-1, "该条数据有人参与报名，不能编辑");
        }



        List<LotteryBall> lotteryBallList = new ArrayList<>();
        //彩票注数
        int zs = getZs(type, typeTwo, typeKl, ballDsStr, numberRedStr, numberBlueStr
                , numberDanStr, numberTuoStr, number3dZx);
        double money = zs * 2d;
        if (ballDsStr != null && ballDsStr.length() > 0) {
            lotteryBallList = JsonUtil.deserialize(ballDsStr
                    , new TypeReference<List<LotteryBall>>() {
                    });
        }
        if ((money * number) > 20000) {
            throw new ServiceInvokeException("单张限额2万");
        }
        LotteryData lotteryData = new LotteryData();
        lotteryData.setId(lotteryDataId);
        lotteryData.setMoney(money);
        lotteryData.setNumber(number);
        portsAdminRepository.updateLotteryData(lotteryData);
        portsAdminRepository.deleteLotteryNumber(lotteryDataId);
        saveLotteryNumber(lotteryBallList, lotteryData, numberRedStr, numberBlueStr, numberDanStr, numberTuoStr
                , number3dZx);
    }

    public void saveLotteryNumber(List<LotteryBall> lotteryBallList, LotteryData lotteryData
            , List<String> numberRedStr, List<String> numberBlueStr, List<String> numberDanStr
            , List<String> numberTuoStr, String number3dZx) {
        List<LotteryNumber> lotteryNumberList = new ArrayList<>();
        if (lotteryBallList.size() > 0) {
            for (LotteryBall lotteryBall : lotteryBallList) {
                LotteryNumber lotteryNumber = new LotteryNumber();
                lotteryNumber.setNumberRed(lotteryBall.getRed());
                lotteryNumber.setNumberBlue(lotteryBall.getBlue());
                lotteryNumber.setLotteryDataId(lotteryData.getId());
                lotteryNumberList.add(lotteryNumber);
            }
        } else {
            LotteryNumber lotteryNumber = new LotteryNumber();
            String red = getNumberString(numberRedStr);
            String blue = getNumberString(numberBlueStr);
            String dan = getNumberString(numberDanStr);
            String tuo = getNumberString(numberTuoStr);

            lotteryNumber.setNumberRed(red);
            lotteryNumber.setNumberBlue(blue);
            lotteryNumber.setNumberDan(dan);
            lotteryNumber.setNumberTuo(tuo);
            lotteryNumber.setLotteryDataId(lotteryData.getId());
            lotteryNumber.setNumberFczx(number3dZx);
            lotteryNumberList.add(lotteryNumber);
        }
        if (lotteryNumberList.size() > 0) {
            portsAdminRepository.saveLotteryNumberList(lotteryNumberList);
        }
    }

    public void updateNumber(Long lotteryDataId, Integer number) {
        LotteryData lotteryData = portsAdminRepository.findLotteryDataBy(lotteryDataId);
        //if (lotteryData != null && lotteryData.getQrCode() != null) {
        //    throw new ServiceInvokeException(-1, "该数据已生成分享二维码，暂不支持修改");
        //}
        List<Long> ids = new ArrayList<>();
        ids.add(lotteryData.getId());
        List<LotteryOrder> orderList = portsAdminRepository.findLotteryOrderList(ids);
        if (orderList != null && orderList.size()>0) {
            throw new ServiceInvokeException(-1, "该数据已有人报名参加，暂不支持修改");
        }

        if (lotteryData != null) {
            if (lotteryData.getMoney() * number > 20000) {
                throw new ServiceInvokeException(-1, "金额不能超过2万");
            }
            portsAdminRepository.updateLotteryData(lotteryDataId, number);
        } else {
            throw new ServiceInvokeException(-1, "数据有误");
        }
    }

    public List<LotteryData> findLotteryData(Long userId, String type) {
        List<LotteryData> lotteryDataList = portsAdminRepository.findLotteryDataList(userId, type);
        List<Long> ids = lotteryDataList.stream().map(c -> c.getId()).collect(Collectors.toList());
        List<LotteryNumber> lotteryNumberList = portsAdminRepository.findLotteryNumberBy(ids);
        for (LotteryData lotteryData : lotteryDataList) {
            List<LotteryNumber> lotteryNumbers = new ArrayList<>();
            for (LotteryNumber lotteryNumber : lotteryNumberList) {
                if (Objects.equals(lotteryData.getId(), lotteryNumber.getLotteryDataId())) {
                    lotteryNumbers.add(lotteryNumber);
                }
            }
            lotteryData.setLotteryNumberList(lotteryNumbers);
        }
        return lotteryDataList;
    }

    public void saveLotteryImg(Long lotteryDataId, String fileUrl) {
        portsAdminRepository.updateLotteryDataImg(lotteryDataId, fileUrl);
    }

     public void saveUserImg(Long userId, String fileUrl) {
         portsAdminRepository.saveUserImg(userId, fileUrl);
    }


    public LotteryData findLotteryDataDetail(Long lotteryId) {
        LotteryData lotteryData = portsAdminRepository.findLotteryDataBy(lotteryId);
        List<Long> ids = new ArrayList<>();
        ids.add(lotteryData.getId());
        List<LotteryNumber> lotteryNumberList = portsAdminRepository.findLotteryNumberBy(ids);
        lotteryData.setLotteryNumberList(lotteryNumberList);
        List<LotteryOrder> lotteryOrderList = portsAdminRepository.findLotteryOrderList(ids);
        lotteryData.setLotteryOrderList(lotteryOrderList);
        return lotteryData;
    }

    public void saveLotteryOrder(Long lotteryId, Long userId) {
        LotteryOrder lotteryOrder = portsAdminRepository.findLotteryOrderBy(lotteryId, userId);
        if (lotteryOrder != null) {
            throw new ServiceInvokeException(-1, "已报名，请勿重复报名");
        }
        portsAdminRepository.saveLotteryOrder(lotteryId, userId, 1);
    }

    public void saveFootballData(FootballDataVO footballDataVO) {
        calculateCost(footballDataVO);
        portsAdminRepository.saveFootballData(footballDataVO);
        for (FootballOddVO footballOddVO : footballDataVO.getFootballOddVOList()) {
            if (footballOddVO.getSpfOdds() != null) {
                footballOddVO.setSpfOddsStr(JsonUtil.serialize(footballOddVO.getSpfOdds()));
            }
            if (footballOddVO.getRqOdds() != null) {
                footballOddVO.setRqOddsStr(JsonUtil.serialize(footballOddVO.getRqOdds()));
            }
            if (footballOddVO.getBfOdds() != null) {
                footballOddVO.setBfOddsStr(JsonUtil.serialize(footballOddVO.getBfOdds()));
            }
            if (footballOddVO.getJqOdds() != null) {
                footballOddVO.setJqOddsStr(JsonUtil.serialize(footballOddVO.getJqOdds()));
            }
            if (footballOddVO.getBqcOdds() != null) {
                footballOddVO.setBqcOddsStr(JsonUtil.serialize(footballOddVO.getBqcOdds()));
            }

            if (footballOddVO.getZhu() != null && footballOddVO.getZhu().size() > 0) {
                footballOddVO.setZhuStr(JsonUtil.serialize(footballOddVO.getZhu()));
            }
            if (footballOddVO.getKe() != null && footballOddVO.getKe().size() > 0) {
                footballOddVO.setKeStr(JsonUtil.serialize(footballOddVO.getKe()));
            }
            if (footballOddVO.getQc() != null && footballOddVO.getQc().size() > 0) {
                footballOddVO.setQcStr(JsonUtil.serialize(footballOddVO.getQc()));
            }
            if (footballOddVO.getBc() != null && footballOddVO.getBc().size() > 0) {
                footballOddVO.setBcStr(JsonUtil.serialize(footballOddVO.getBc()));
            }
        }
        portsAdminRepository.saveFootballOddVOList(footballDataVO.getId(), footballDataVO.getFootballOddVOList());
    }

    private boolean isNullSelect(FootballOddVO obj){
        if(obj.getSpfOdds()!=null){
            if(obj.getSpfOdds().oddList().size()>0){
                return true;
            }
            return false;
        }else  if(obj.getRqOdds()!=null){
            if(obj.getRqOdds().oddList().size()>0){
                return true;
            }
            return false;
        }else  if(obj.getBfOdds()!=null){
            if(obj.getBfOdds().bfOddList().size()>0){
                return true;
            }
            return false;
        }
        return true;
    }

    /**
     * 算多少注 最高中奖金额 和最低中奖金额
     *
     * @param footballDataVO
     * @return
     */
    public CostVO calculateCost(FootballDataVO footballDataVO) {
        CostVO costVO = new CostVO();
        double maxMoney = 0;
        double minMoney = 0;
        Integer zhu = 0;
        List<FootballOddVO> newfootballOddVOList = footballDataVO.getFootballOddVOList();
        List<FootballOddVO> footballOddVOList = new ArrayList<FootballOddVO>();
        for (FootballOddVO footballOddVO0:newfootballOddVOList){
            if(this.isNullSelect(footballOddVO0)){
                footballOddVOList.add(footballOddVO0);
            }
        }
        String strand =footballOddVOList.size()+""; //footballDataVO.getStrand();
        if (Objects.equals(footballDataVO.getCategory(), "jc") || Objects.equals(footballDataVO.getCategory(), "bjdc")
                || Objects.equals(footballDataVO.getCategory(), "rx")
                || Objects.equals(footballDataVO.getCategory(), "sf")) {
            //竞猜足球算法   b北京单场算法
            List<List<Double>> oddList = new ArrayList<>();
            //每一组最高的倍率单独存起来
            List<Double> maxOddList = new ArrayList<>();
            for (FootballOddVO footballOddVO : footballOddVOList) {
                if (Objects.equals(footballDataVO.getType(), "mix")) {
                    //混合过关
                } else if (Objects.equals(footballDataVO.getType(), "spfRq")) {
                    if (footballOddVO.getSpfOdds() != null) {
                        List<Double> doubleList = footballOddVO.getSpfOdds().oddList();
                        oddList.add(doubleList);
                        maxOddList.add(Collections.max(doubleList));
                    }
                    if (footballOddVO.getRqOdds() != null) {
                        List<Double> doubleList = footballOddVO.getRqOdds().oddList();
                        oddList.add(doubleList);
                        maxOddList.add(Collections.max(doubleList));
                    }
                } else if (Objects.equals(footballDataVO.getType(), "spf")) {
                    if (footballOddVO.getSpfOdds() != null) {
                        List<Double> doubleList = footballOddVO.getSpfOdds().oddList();
                        oddList.add(doubleList);
                        maxOddList.add(Collections.max(doubleList));
                    }
                } else if (Objects.equals(footballDataVO.getType(), "bf")) {
                    if (footballOddVO.getBfOdds() != null) {
                        List<Double> doubleList = footballOddVO.getBfOdds().bfOddList();
                        oddList.add(doubleList);
                        maxOddList.add(Collections.max(doubleList));
                    }
                }
            }
            if (StringUtils.isBlank(strand)) {
                throw new ServiceInvokeException(-1, "过关方式参数错误");
            }

            List<String> split = Arrays.asList(strand.split(","));
            for (String s : split) {
                int x = Integer.parseInt(s);
                if (Objects.equals(1, x)) {
                    //单关
                    for (List<Double> d : oddList) {
                        zhu += d.size();
                        if (d.size() > 2) {
                            Double min = Collections.min(d);
                            Double max = Collections.max(d);
                            maxMoney += max * 2;
                            if (minMoney == 0) {
                                minMoney = min * 2;
                            } else {
                                if (minMoney > (min * 2)) {
                                    minMoney = min;
                                }
                            }

                        } else {
                            Double max = Collections.max(d);
                            maxMoney = max * 2;
                        }
                    }
                } else {
                    List<List<Double>> combinations = CombinationUtil.getCombinations(oddList, x, 0
                            , new ArrayList<>(), new boolean[oddList.size()]);
                    // 打印所有组合
                    combinations.forEach(combination -> System.out.println(combination));
                    System.out.println("长度：{}" + combinations.size());
                    zhu += combinations.size();
                    CombinationUtil.Num num2 = CombinationUtil.getNum2(combinations, 0);
                    double min = num2.getMin();
                    if (minMoney == 0) {
                        minMoney = min;
                    } else if (minMoney > min) {
                        minMoney = min;
                    }
                    List<List<Double>> result = new ArrayList<>();
                    CombinationUtil.generate(maxOddList, 0, x, new ArrayList<Double>(), result);
                    CombinationUtil.Num num = CombinationUtil.getNum2(result, x);
                    maxMoney += num.getMax();
                }
            }
        } else if (Objects.equals(footballDataVO.getCategory(), "jq") || Objects.equals(footballDataVO.getCategory(), "bqc")) {
            int i = 1;
            for (FootballOddVO footballOddVO : footballOddVOList) {
                List<List<Integer>> lists = new ArrayList<>();
                if (Objects.equals(footballDataVO.getCategory(), "jq")) {
                    //进球 主队 客队
                    lists.add(footballOddVO.getZhu());
                    lists.add(footballOddVO.getKe());
                } else if (Objects.equals(footballDataVO.getCategory(), "bqc")) {
                    //半全场  半场 全场
                    lists.add(footballOddVO.getQc());
                    lists.add(footballOddVO.getBc());
                }

                List<List<Integer>> combinations = CombinationUtil.getCombinations(lists, 2, 0, new ArrayList<>(), new boolean[lists.size()]);
                int size = combinations.size();
                i *= size;
            }
            zhu = i;
        }
        if (Objects.equals(footballDataVO.getCategory(), "bjdc")) {
            maxMoney = maxMoney * 0.65;
            minMoney = minMoney * 0.65;
        }

        ////DecimalFormat df = new DecimalFormat("##.##");
        ////System.out.println("最小值："+ min);
        ////System.out.println("最小值："+ df.format(min));
        //
        if (maxMoney == 0) {
            footballDataVO.setMaxMoney(0.0);
            costVO.setMaxMoney(0.0);
        } else {
            footballDataVO.setMaxMoney(Math.round(maxMoney * 100) / 100.0);
            costVO.setMaxMoney(Math.round(maxMoney * 100) / 100.0);
        }
        if (minMoney == 0) {
            footballDataVO.setMinMoney(0.0);
            costVO.setMinMoney(0.0);
        } else {
            footballDataVO.setMinMoney(Math.round(minMoney * 100) / 100.0);
            costVO.setMinMoney(Math.round(minMoney * 100) / 100.0);
        }

        footballDataVO.setZhu(zhu);
        footballDataVO.setCostMoney(zhu * 2);

        costVO.setZhu(zhu);
        costVO.setCostMoney(zhu * 2);
        return costVO;
    }

    public CostVO calculateFootballData(FootballDataVO footballDataVO) {
        CostVO costVO = calculateCost(footballDataVO);
        return costVO;
    }

    public List<FootballDataVO> findFootballData(String type, Long userId, String category) {
        List<FootballDataVO> footballDataVOList = portsAdminRepository.findFootballDataList(type, userId, category, null);
        List<Long> fdIds = footballDataVOList.stream().map(c -> c.getId()).collect(Collectors.toList());
        List<FootballOddVOTwo> footballOddVOTwoList = portsAdminRepository.findFootballOddList(fdIds);
        fillData(footballDataVOList, footballOddVOTwoList);
        return footballDataVOList;
    }

    public void fillData(List<FootballDataVO> footballDataVOList, List<FootballOddVOTwo> footballOddVOTwoList) {
        List<Integer> matchIds = footballOddVOTwoList.stream().map(c -> c.getMatchId()).collect(Collectors.toList());
        List<FootballResult> footballResultList = portsAdminRepository.findFootballJcResultBy(matchIds);
        if (footballResultList != null) {
            for (FootballOddVOTwo footballOddVOTwo : footballOddVOTwoList) {
                for (FootballResult footballResult : footballResultList) {
                    if (Objects.equals(footballOddVOTwo.getMatchId(), footballResult.getMatchId())) {
                        footballOddVOTwo.setFullTimeScore(footballOddVOTwo.getFullTimeScore());
                        break;
                    }
                }
            }
        }

        for (FootballDataVO footballDataVO : footballDataVOList) {
            List<FootballOddVOTwo> footballOddVOTwos = new ArrayList<>();
            fillFootballOddVOTwo(footballOddVOTwos, footballDataVO, footballOddVOTwoList);
            footballDataVO.setFootballOddVOTwoList(footballOddVOTwos);
        }
    }

    public void fillFootballOddVOTwo(List<FootballOddVOTwo> footballOddVOTwos, FootballDataVO footballDataVO
            , List<FootballOddVOTwo> footballOddVOTwoList) {
        for (FootballOddVOTwo footballOddVOTwo : footballOddVOTwoList) {
            if (Objects.equals(footballDataVO.getId(), footballOddVOTwo.getFootBallDataId())) {
                if (StringUtils.isNotEmpty(footballOddVOTwo.getSpfOdds())) {
                    footballOddVOTwo.setSpfOddList(JsonUtil.deserialize(footballOddVOTwo.getSpfOdds(), SpfOdd.class));
                }
                if (StringUtils.isNotEmpty(footballOddVOTwo.getRqOdds())) {
                    footballOddVOTwo.setRqOddList(JsonUtil.deserialize(footballOddVOTwo.getRqOdds(), RqOdd.class));
                }
                if (StringUtils.isNotEmpty(footballOddVOTwo.getBfOdds())) {
                    footballOddVOTwo.setBfOddList(JsonUtil.deserialize(footballOddVOTwo.getBfOdds(), BfOdd.class));
                }
                if (StringUtils.isNotEmpty(footballOddVOTwo.getJqOdds())) {
                    footballOddVOTwo.setJqOddList(JsonUtil.deserialize(footballOddVOTwo.getJqOdds(), JqOdd.class));
                }
                if (StringUtils.isNotEmpty(footballOddVOTwo.getBqcOdds())) {
                    footballOddVOTwo.setBqcOddList(JsonUtil.deserialize(footballOddVOTwo.getBqcOdds(), BqcOdd.class));
                }
                footballOddVOTwos.add(footballOddVOTwo);
            }
        }
    }

    public void saveFootballDataImg(Long footballDataId, String fileUrl) {
        portsAdminRepository.updateFootballQrCode(footballDataId, fileUrl);
    }


    public List<FootballFixture> findFootballFixtureBy(List<Integer> matchIds) {
        return portsAdminRepository.findFootballFixtureBy(matchIds);
    }

    public void saveFootballOrder(Long footballDataId, Long userId) {
        FootballOrder footballOrder = portsAdminRepository.findFootballOrderBy(footballDataId, userId);
        if (footballOrder != null) {
            throw new ServiceInvokeException(-1, "已报名，请勿重复报名");
        }
        portsAdminRepository.saveFootballOrder(footballDataId, userId, 1);
    }

    public List<FootballDataVO> findFootballDataByOrder(String type, Long userId, String category) {
        List<FootballOrder> footballOrderList = portsAdminRepository.findFootballOrderList(userId);
        List<Long> dataIds = footballOrderList.stream().map(c -> c.getFootballDataId()).collect(Collectors.toList());
        List<FootballDataVO> footballDataVOList = portsAdminRepository.findFootballDataList(type, null, category, dataIds);
        List<Long> fdIds = footballDataVOList.stream().map(c -> c.getId()).collect(Collectors.toList());
        List<FootballOddVOTwo> footballOddVOTwoList = portsAdminRepository.findFootballOddList(fdIds);

        fillData(footballDataVOList, footballOddVOTwoList);
        return footballDataVOList;
    }

    public FootballDataVO findFootballDetail(Long footballDataId) {
        FootballDataVO footballDataVO = portsAdminRepository.findFootballDataVOBy(footballDataId);
        List<Long> dataIds = new ArrayList<>();
        dataIds.add(footballDataVO.getId());
        List<FootballOddVOTwo> footballOddVOTwoList = portsAdminRepository.findFootballOddList(dataIds);
        List<FootballOddVOTwo> footballOddVOTwos = new ArrayList<>();
        fillFootballOddVOTwo(footballOddVOTwos, footballDataVO, footballOddVOTwoList);
        footballDataVO.setFootballOddVOTwoList(footballOddVOTwos);
        List<FootballOrder> footballOrderList = portsAdminRepository.findFootballOrderBy(footballDataId);
        footballDataVO.setFootballOrderList(footballOrderList);
        return footballDataVO;
    }

    public void deleteFootballData(List<Long> footballDataIds,Long userId) {
        for (Long footballDataId : footballDataIds) {
            FootballDataVO footballData = portsAdminRepository.findFootballDataVOBy(footballDataId);
            if(footballData!=null){
                if(!Objects.equals(footballData.getUserId(),userId)){
                    throw new ServiceInvokeException(-1, "选择的数据是参与报名数据，不是本人没有删除权限");
                }
                List<FootballOrder> orderList = portsAdminRepository.findFootballOrderBy(footballDataId);
                if(orderList!=null && orderList.size()>0){
                    throw new ServiceInvokeException(-1, "选择的数据有人参与报名，不能删除");
                }
            }
        }
        portsAdminRepository.deleteFootballData(footballDataIds);
        portsAdminRepository.deleteFootballOdd(footballDataIds);
        portsAdminRepository.deleteFootballOrder(footballDataIds);
    }


    public List<LotteryData> findLotteryDataBy(String openDate) {
        List<LotteryData> lotteryDataList = portsAdminRepository.findLotteryDataBy(openDate);
        return lotteryDataList;
    }

    public void updateBatchLotteryData(List<LotteryData> lotteryDataList) {
        portsAdminRepository.updateBatchLotteryData(lotteryDataList);
    }

    public List<LotteryData> findLotteryDataByOrder(Long userId) {
        List<LotteryOrder> lotteryOrderList = portsAdminRepository.findLotteryDataOrderList(userId);
        List<Long> dataIds = lotteryOrderList.stream().map(c -> c.getLotteryDataId()).collect(Collectors.toList());
        List<LotteryData> lotteryDataList = portsAdminRepository.findLotteryDataByIds(dataIds);
        List<Long> ids = lotteryDataList.stream().map(c -> c.getId()).collect(Collectors.toList());
        List<LotteryNumber> lotteryNumberList = portsAdminRepository.findLotteryNumberBy(ids);
        for (LotteryData lotteryData : lotteryDataList) {
            List<LotteryNumber> lotteryNumbers = new ArrayList<>();
            for (LotteryNumber lotteryNumber : lotteryNumberList) {
                if (Objects.equals(lotteryData.getId(), lotteryNumber.getLotteryDataId())) {
                    lotteryNumbers.add(lotteryNumber);
                }
            }
            lotteryData.setLotteryNumberList(lotteryNumbers);
        }
        return lotteryDataList;
    }

    public void updateFootballDataNumber(Long footballDataId, Integer number) {
        FootballDataVO footballData = portsAdminRepository.findFootballDataVOBy(footballDataId);
        List<FootballOrder> orderList = portsAdminRepository.findFootballOrderBy(footballData.getId());
        if (orderList != null && orderList.size()>0) {
            throw new ServiceInvokeException(-1, "该数据已有人报名参加，暂不支持修改");
        }
        portsAdminRepository.updateFootballDataNumber(footballDataId, number);
    }

    public AppVersion findAppVersion(String version) {
        AppVersion appVersion = portsAdminRepository.findAppVersion(version);
        return appVersion;
    }

}



